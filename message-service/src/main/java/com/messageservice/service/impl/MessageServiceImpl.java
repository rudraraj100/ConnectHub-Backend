package com.messageservice.service.impl;

import com.messageservice.client.AuthServiceClient;
import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.entity.Message;
import com.messageservice.repository.MessageRepository;
import com.messageservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MessageServiceImpl — case study §4.3
 *
 * Business logic for all message operations.
 * Reads X-User-Id from the header (injected by API Gateway JwtGatewayFilter).
 * No Spring Security — security is the gateway's responsibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final AuthServiceClient authServiceClient;

    // ── Enrich ───────────────────────────────────────────────────────

    /**
     * Attempts to enrich a message with sender name and avatar from auth-service.
     * Falls back gracefully if auth-service is unreachable.
     */
    private MessageResponse toResponse(Message m) {
        String senderName   = m.getSenderId();
        String senderAvatar = null;

        try {
            Map<String, Object> res = authServiceClient.getUserById(m.getSenderId());
            if (res != null && res.get("data") instanceof Map<?, ?> data) {
                senderName   = (String) data.get("fullName");
                senderAvatar = (String) data.get("avatarUrl");
            }
        } catch (Exception ex) {
            log.debug("Could not enrich sender info for {}: {}", m.getSenderId(), ex.getMessage());
        }

        return MessageResponse.builder()
                .messageId(m.getMessageId())
                .roomId(m.getRoomId())
                .senderId(m.getSenderId())
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .content(m.isDeleted() ? "[This message was deleted]" : m.getContent())
                .type(m.getType())
                .mediaUrl(m.getMediaUrl())
                .replyToMessageId(m.getReplyToMessageId())
                .isEdited(m.isEdited())
                .isDeleted(m.isDeleted())
                .deliveryStatus(m.getDeliveryStatus())
                .sentAt(m.getSentAt())
                .editedAt(m.getEditedAt())
                .build();
    }

    // ── Send ─────────────────────────────────────────────────────────

    @Override
    public MessageResponse sendMessage(String roomId, String senderId, SendMessageRequest req) {
        Message message = Message.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(req.getContent())
                .type(req.getType() != null ? req.getType() : "TEXT")
                .mediaUrl(req.getMediaUrl())
                .replyToMessageId(req.getReplyToMessageId())
                .deliveryStatus("SENT")
                .build();

        Message saved = messageRepository.save(message);
        log.debug("Message {} saved to room {}", saved.getMessageId(), roomId);
        return toResponse(saved);
    }

    // ── Retrieve ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<MessageResponse> getMessageById(String messageId) {
        return messageRepository.findByMessageId(messageId).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesByRoom(String roomId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        return messageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBefore(String roomId, LocalDateTime before) {
        return messageRepository.findByRoomIdAndSentAtAfter(roomId, before)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Edit ──────────────────────────────────────────────────────────

    @Override
    public MessageResponse editMessage(String messageId, String requesterId, EditMessageRequest req) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!message.getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own messages");
        }
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit a deleted message");
        }

        message.setContent(req.getContent());
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());

        return toResponse(messageRepository.save(message));
    }

    // ── Delete ────────────────────────────────────────────────────────

    @Override
    public void deleteMessage(String messageId, String requesterId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // Sender can delete own; room admins are checked at controller level via X-User-Role
        if (!message.getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own messages");
        }

        message.setDeleted(true);
        messageRepository.save(message);
        log.debug("Message {} soft-deleted by {}", messageId, requesterId);
    }

    // ── Search ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(String roomId, String keyword) {
        return messageRepository.searchInRoom(roomId, keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Delivery Status ───────────────────────────────────────────────

    @Override
    public void updateDeliveryStatus(String messageId, String status) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        message.setDeliveryStatus(status);
        messageRepository.save(message);
    }

    // ── Count / Unread ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long getMessageCount(String roomId) {
        return messageRepository.countByRoomId(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getUnreadMessages(String roomId, LocalDateTime since) {
        return messageRepository.findByRoomIdAndSentAtAfter(roomId, since)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
