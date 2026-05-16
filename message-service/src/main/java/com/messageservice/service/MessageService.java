package com.messageservice.service;

import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.MessageResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MessageService interface — case study §4.3
 *
 * Declares all message persistence, pagination, editing, soft-deletion,
 * search, delivery status update, and unread retrieval operations.
 *
 * Bug 1 fix: added markAllAsReadInRoom() — see implementation for details.
 */
public interface MessageService {

    /** Persist a new message and return the saved response */
    MessageResponse sendMessage(String roomId, String senderId, SendMessageRequest request);

    /** Fetch a single message by its ID */
    Optional<MessageResponse> getMessageById(String messageId);

    /** Paginated room history — newest first (infinite scroll) */
    Page<MessageResponse> getMessagesByRoom(String roomId, int page, int size);

    /** Plan-aware history: PREMIUM = unlimited, FREE = last 30 days. */
    Page<MessageResponse> getMessagesByRoom(String roomId, int page, int size, String plan);

    /** Messages sent before a given timestamp (for scroll-up loading) */
    List<MessageResponse> getMessagesBefore(String roomId, LocalDateTime before);

    /** Edit a message — sets isEdited=true, updates content and editedAt */
    MessageResponse editMessage(String messageId, String requesterId, EditMessageRequest request);

    /** Soft-delete a message — sets isDeleted=true */
    void deleteMessage(String messageId, String requesterId);

    /** Full-text keyword search within a room */
    List<MessageResponse> searchMessages(String roomId, String keyword);

    /** Update delivery status: SENT → DELIVERED → READ (single message) */
    void updateDeliveryStatus(String messageId, String status);

    /** Total message count for a room */
    long getMessageCount(String roomId);

    /** Messages in a room after a given timestamp (unread messages) */
    List<MessageResponse> getUnreadMessages(String roomId, LocalDateTime since);

    // ── Bug 1 fix ─────────────────────────────────────────────────────────────

    /**
     * Marks ALL messages from OTHER users in a room as READ in one query.
     *
     * Called by the WebSocket handler when a READ_RECEIPT arrives, replacing the
     * old single-row update. Returns the number of rows updated (useful for logging).
     *
     * @param roomId   the room in which the read happened
     * @param readerId the user who READ the messages (their own messages are NOT touched)
     */
    int markAllAsReadInRoom(String roomId, String readerId);

    // ── Pin message (PREMIUM feature) ──────────────────────────────────

    /**
     * Unpin all messages in the room, then pin the specified message.
     * Called by the room-service after admin + plan validation.
     */
    void pinMessage(String roomId, String messageId);

    /** Unpin the currently-pinned message in a room. */
    void unpinMessage(String roomId);
}