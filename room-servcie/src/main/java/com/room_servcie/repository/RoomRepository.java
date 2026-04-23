package com.room_servcie.repository;

import com.room_servcie.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    /** All rooms a user belongs to, sorted newest activity first — §2.4 */
    @Query("""
        SELECT r FROM Room r
        JOIN r.members m
        WHERE m.userId = :userId
        ORDER BY r.lastMessageAt DESC NULLS LAST
        """)
    List<Room> findRoomsByUserId(@Param("userId") String userId);

    /** Find existing DM between exactly 2 users */
    @Query("""
        SELECT r FROM Room r
        JOIN r.members m1 ON m1.userId = :user1
        JOIN r.members m2 ON m2.userId = :user2
        WHERE r.type = 'DM'
        """)
    Optional<Room> findDmBetween(@Param("user1") String user1, @Param("user2") String user2);

    Optional<Room> findByInviteLink(String inviteLink);
}
