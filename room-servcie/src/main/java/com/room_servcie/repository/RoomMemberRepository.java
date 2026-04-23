package com.room_servcie.repository;

import com.room_servcie.entity.RoomMember;
import com.room_servcie.entity.RoomMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {

    List<RoomMember> findByRoom_RoomId(String roomId);

    Optional<RoomMember> findByRoom_RoomIdAndUserId(String roomId, String userId);

    boolean existsByRoom_RoomIdAndUserId(String roomId, String userId);

    long countByRoom_RoomId(String roomId);

    List<RoomMember> findByRoom_RoomIdAndRole(String roomId, RoomMemberRole role);

    void deleteByRoom_RoomIdAndUserId(String roomId, String userId);
}
