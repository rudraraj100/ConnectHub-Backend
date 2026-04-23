package com.room_servcie.repository;

import com.room_servcie.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /** Paginated message history, newest first — §2.4 infinite scroll */
    Page<Message> findByRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(String roomId, Pageable pageable);

    /** Count unread messages after lastReadAt for a specific room+user pair */
    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.roomId = :roomId
          AND m.isDeleted = false
          AND m.createdAt > (
              SELECT rm.lastReadAt FROM RoomMember rm
              WHERE rm.room.roomId = :roomId AND rm.userId = :userId
          )
        """)
    long countUnread(@Param("roomId") String roomId, @Param("userId") String userId);

    /** Pinned message for a room */
    Optional<Message> findByRoomIdAndIsPinnedTrue(String roomId);

    /** Soft delete all messages in a room — §2.5 clear history */
    @Modifying
    @Query("UPDATE Message m SET m.isDeleted = true WHERE m.roomId = :roomId")
    void softDeleteAllByRoomId(@Param("roomId") String roomId);
}
