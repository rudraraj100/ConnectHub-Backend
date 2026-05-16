package com.room_servcie.service;

import com.room_servcie.dto.request.*;
import com.room_servcie.dto.response.*;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoomService {

    // ── Room CRUD ──────────────────────────────────────────────
    RoomResponse createRoom(String creatorId, CreateRoomRequest req);
    RoomResponse getRoomById(String roomId, String requesterId);
    RoomResponse updateRoom(String roomId, String requesterId, UpdateRoomRequest req);
    List<RoomResponse> getMyRooms(String userId);

    // ── Membership ─────────────────────────────────────────────
    RoomMemberResponse addMember(String roomId, String adminId, AddMemberRequest req, String token);
    void removeMember(String roomId, String adminId, String targetUserId);
    void leaveRoom(String roomId, String userId);
    RoomResponse joinByInviteLink(String inviteLink, String userId);
    List<RoomMemberResponse> getMembers(String roomId, String requesterId, String token);

    // ── Room Admin actions ─────────────────────────────────────
    RoomMemberResponse changeRole(String roomId, String adminId, String targetUserId, String newRole);
    RoomMemberResponse toggleMute(String roomId, String adminId, String targetUserId);

    // ── Messages ───────────────────────────────────────────────
    MessageResponse sendMessage(String roomId, String senderId, SendMessageRequest req);
    Page<MessageResponse> getMessages(String roomId, String requesterId, int page, int size);
    void deleteMessage(String roomId, String requesterId, String messageId);
    void clearHistory(String roomId, String adminId);
    MessageResponse pinMessage(String roomId, String adminId, String messageId);
    void unpinMessage(String roomId, String adminId);

    // ── Unread ─────────────────────────────────────────────────
    void markAsRead(String roomId, String userId);

    // ── Platform Admin ─────────────────────────────────────────
    List<RoomResponse> getAllRooms(String requesterId);
    void adminDeleteRoom(String requesterId, String roomId);
}
