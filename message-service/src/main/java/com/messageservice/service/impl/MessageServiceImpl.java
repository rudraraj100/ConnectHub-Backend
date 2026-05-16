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
 *
 * Bug 1 fix:
 *   Added markAllAsReadInRoom() — delegates to the new @Modifying repository
 *   query that bulk-updates every SENT/DELIVERED message from a given sender
 *   in a room to READ in a single SQL UPDATE.
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
        // Priority 1: name stored in DB at send-time (from frontend profile via WS payload).
        // This avoids any service-to-service call entirely for new messages.
        String senderName     = m.getSenderName();
        String senderUsername = null;
        String senderAvatar   = null;

        // Priority 2: only call auth-service for historical messages (senderName == null in DB).
        if (senderName == null || senderName.isBlank()) {
            senderName = m.getSenderId(); // safe UUID fallback before we try Feign
            try {
                Map<String, Object> res = authServiceClient.getUserById(m.getSenderId());
                if (res != null && res.get("data") instanceof Map<?, ?> data) {
                    String fullName = (String) data.get("fullName");
                    String username = (String) data.get("username");
                    senderUsername  = username;
                    senderAvatar    = (String) data.get("avatarUrl");

                    // Prefer fullName → username → UUID (never null)
                    if (fullName != null && !fullName.isBlank()) {
                        senderName = fullName;
                    } else if (username != null && !username.isBlank()) {
                        senderName = username;
                    }
                }
            } catch (Exception ex) {
                log.debug("Could not enrich sender info for {}: {}", m.getSenderId(), ex.getMessage());
            }
        }

        return MessageResponse.builder()
                .messageId(m.getMessageId())
                .roomId(m.getRoomId())
                .senderId(m.getSenderId())
                .senderName(senderName)         // never null: fullName → username → UUID
                .senderUsername(senderUsername) // raw @handle if available
                .senderAvatar(senderAvatar)
                .content(m.isDeleted() ? "[This message was deleted]" : m.getContent())
                .type(m.getType())
                .mediaType(deriveMediaType(m.getType()))
                .mediaUrl(m.getMediaUrl())
                .replyToMessageId(m.getReplyToMessageId())
                .isEdited(m.isEdited())
                .isDeleted(m.isDeleted())
                .isPinned(m.isPinned())          // PREMIUM: banner appears on reload
                .deliveryStatus(m.getDeliveryStatus())
                .sentAt(m.getSentAt())
                .editedAt(m.getEditedAt())
                .build();
    }

    /** Maps the persisted message type to the mediaType field Angular checks for rendering. */
    private String deriveMediaType(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "IMAGE" -> "IMAGE";
            case "VIDEO" -> "VIDEO";
            case "FILE"  -> "FILE";
            default      -> null;
        };
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
                .senderName(req.getSenderName())   // persist display name so toResponse() skips Feign
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
        return getMessagesByRoom(roomId, page, size, "FREE");  // backward-compat default
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesByRoom(String roomId, int page, int size, String plan) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        if ("PREMIUM".equals(plan)) {
            // Unlimited history
            return messageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable)
                    .map(this::toResponse);
        }
        // FREE: cap at 30 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        return messageRepository.findByRoomIdAndSentAtAfterOrderBySentAtDesc(roomId, cutoff, pageable)
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

    // ── Bug 1 fix ─────────────────────────────────────────────────────

    /**
     * Bulk-marks every SENT/DELIVERED message from {@code senderId} in
     * {@code roomId} as READ, using a single SQL UPDATE.
     *
     * Why senderId is the OTHER user (not the reader):
     *   In a 1-to-1 chat, when User B reads the conversation, we want to flip
     *   User A's outgoing messages to READ so User A sees the blue ticks.
     *   User B's own messages don't need to change.
     *
     * The @Modifying query in MessageRepository handles the bulk UPDATE.
     * We annotate this method @Transactional (inherited from the class) so
     * the @Modifying query runs inside a proper write transaction.
     */
    @Override
    public int markAllAsReadInRoom(String roomId, String readerId) {
        int updated = messageRepository.markAllAsReadInRoom(roomId, readerId);
        log.debug("[Status] Marked {} messages READ in room {} (readerId={})", updated, roomId, readerId);
        return updated;
    }

    // ── Pin message (PREMIUM feature) ────────────────────────────────

    /**
     * Unpin all messages in the room, then pin the specified message.
     * Uses two bulk UPDATE queries so no row-by-row loading is needed.
     *
     * This is called by room-service AFTER it has already:
     *   • Verified the requester is a room ADMIN
     *   • Verified the user has a PREMIUM plan
     *   • Pinned the message in its own DB (connecthub_rooms)
     * We mirror the flag in message-service DB (connecthub_messages) so the
     * history API returns isPinned=true and the banner survives page reload.
     */
    @Override
    public void pinMessage(String roomId, String messageId) {
        messageRepository.unpinAllInRoom(roomId);   // ensure at most one pin per room
        int rows = messageRepository.pinMessage(messageId);
        log.debug("[Pin] Pinned message {} in room {} ({} rows updated)", messageId, roomId, rows);
    }

    @Override
    public void unpinMessage(String roomId) {
        int rows = messageRepository.unpinAllInRoom(roomId);
        log.debug("[Pin] Unpinned all messages in room {} ({} rows updated)", roomId, rows);
    }
}