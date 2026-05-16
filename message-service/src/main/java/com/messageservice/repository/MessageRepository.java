package com.messageservice.repository;

import com.messageservice.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MessageRepository — case study §4.3
 *
 * findByRoomId(), findByRoomIdOrderBySentAtDesc(), findBySenderId(),
 * findByMessageId(), findByRoomIdAndSentAtAfter(), searchInRoom(),
 * countByRoomId(), deleteByMessageId()
 *
 * Bug 1 fix: added markAllAsReadInRoom() — bulk-updates every SENT/DELIVERED
 * message in a room to READ in a single UPDATE statement.
 * The old approach (persistDeliveryStatus on a single messageId) meant that
 * after a page refresh, only the last message would show READ; all earlier
 * messages reverted to SENT.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /** All messages in a room (unordered — use with Pageable) */
    List<Message> findByRoomId(String roomId);

    /** Paginated history — newest first (infinite scroll §2.4) */
    Page<Message> findByRoomIdOrderBySentAtDesc(String roomId, Pageable pageable);

    /** Paginated history with date cutoff — used for FREE plan users (30-day limit). */
    Page<Message> findByRoomIdAndSentAtAfterOrderBySentAtDesc(String roomId,
                                                               LocalDateTime after,
                                                               Pageable pageable);

    /** All messages from a specific sender */
    List<Message> findBySenderId(String senderId);

    /** Lookup by primary key */
    Optional<Message> findByMessageId(String messageId);

    /** Messages after a given timestamp — used for unread computation */
    List<Message> findByRoomIdAndSentAtAfter(String roomId, LocalDateTime after);

    /** Full-text keyword search within a room §2.3 */
    @Query("SELECT m FROM Message m WHERE m.roomId = :roomId " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND m.isDeleted = false ORDER BY m.sentAt DESC")
    List<Message> searchInRoom(@Param("roomId") String roomId,
                               @Param("keyword") String keyword);

    /** Total message count for a room */
    long countByRoomId(String roomId);

    /** Hard delete by messageId (used by clear history or admin delete) */
    void deleteByMessageId(String messageId);

    /** Count messages after timestamp — used for unread count per user */
    long countByRoomIdAndSentAtAfter(String roomId, LocalDateTime after);

    // ── Bug 1 fix ─────────────────────────────────────────────────────────────

    /**
     * Bulk-marks every non-READ message in a room as READ in one UPDATE.
     *
     * Why this is needed:
     *   The old code called persistDeliveryStatus(upToMessageId, "READ") which
     *   updated exactly ONE row. On page refresh, only that row retained READ
     *   status — every earlier message showed a single grey tick instead of the
     *   expected blue double-tick.
     *
     * This query is scoped to :senderId so we only flip messages that belong to
     * the other user (the sender) — not the reader's own outgoing messages.
     * This matches WhatsApp / Telegram semantics: your read receipt turns the
     * SENDER's ticks blue, not your own.
     *
     * Requires @Modifying + @Transactional on the calling service method.
     */
    @Modifying
    @Query("UPDATE Message m SET m.deliveryStatus = 'READ' " +
           "WHERE m.roomId = :roomId " +
           "AND m.senderId != :readerId " +
           "AND m.deliveryStatus <> 'READ'")
    int markAllAsReadInRoom(@Param("roomId")   String roomId,
                            @Param("readerId") String readerId);

    // ── Pin message (PREMIUM feature) ──────────────────────────────────────────

    /**
     * Unpin every pinned message in a room (called before pinning a new one
     * and when the room admin explicitly unpins).
     */
    @Modifying
    @Query("UPDATE Message m SET m.isPinned = false WHERE m.roomId = :roomId AND m.isPinned = true")
    int unpinAllInRoom(@Param("roomId") String roomId);

    /**
     * Pin a specific message identified by messageId in a room.
     * The caller should call unpinAllInRoom first so at most one message is pinned.
     */
    @Modifying
    @Query("UPDATE Message m SET m.isPinned = true WHERE m.messageId = :messageId")
    int pinMessage(@Param("messageId") String messageId);
}