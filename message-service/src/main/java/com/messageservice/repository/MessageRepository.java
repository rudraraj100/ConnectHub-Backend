package com.messageservice.repository;

import com.messageservice.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /** All messages in a room (unordered — use with Pageable) */
    List<Message> findByRoomId(String roomId);

    /** Paginated history — newest first (infinite scroll §2.4) */
    Page<Message> findByRoomIdOrderBySentAtDesc(String roomId, Pageable pageable);

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
}
