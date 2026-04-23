package com.room_servcie.service.impl;

import com.room_servcie.client.AuthServiceClient;
import com.room_servcie.dto.request.*;
import com.room_servcie.dto.response.*;
import com.room_servcie.entity.*;
import com.room_servcie.repository.*;
import com.room_servcie.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RoomServiceImpl implements RoomService {

    private final RoomRepository       roomRepo;
    private final RoomMemberRepository memberRepo;
    private final MessageRepository    messageRepo;
    private final AuthServiceClient    authClient;

    // ═══════════════════════════════════════════════════════════
    // ROOM CRUD
    // ═══════════════════════════════════════════════════════════

    @Override
    public RoomResponse createRoom(String creatorId, CreateRoomRequest req) {
        // DM: check if one already exists between the two users
        if (req.getType() == RoomType.DM) {
            if (req.getTargetUserId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUserId is required for DM");
            }
            roomRepo.findDmBetween(creatorId, req.getTargetUserId()).ifPresent(dm -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "DM already exists");
            });
        }

        Room room = Room.builder()
            .name(req.getName())
            .description(req.getDescription())
            .type(req.getType())
            .avatarUrl(req.getAvatarUrl())
            .maxMembers(req.getMaxMembers())
            .createdBy(creatorId)
            .build();
        roomRepo.save(room);

        // Add creator as ADMIN
        addMemberInternal(room, creatorId, RoomMemberRole.ADMIN);

        // For DM add the target user
        if (req.getType() == RoomType.DM && req.getTargetUserId() != null) {
            addMemberInternal(room, req.getTargetUserId(), RoomMemberRole.MEMBER);
        }

        return toRoomResponse(room, creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(String roomId, String requesterId) {
        Room room = findRoom(roomId);
        assertMember(room, requesterId);
        return toRoomResponse(room, requesterId);
    }

    @Override
    public RoomResponse updateRoom(String roomId, String requesterId, UpdateRoomRequest req) {
        Room room = findRoom(roomId);
        assertAdmin(room, requesterId);
        if (req.getName()        != null) room.setName(req.getName());
        if (req.getDescription() != null) room.setDescription(req.getDescription());
        if (req.getAvatarUrl()   != null) room.setAvatarUrl(req.getAvatarUrl());
        if (req.getMaxMembers()  != null) room.setMaxMembers(req.getMaxMembers());
        return toRoomResponse(roomRepo.save(room), requesterId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String userId) {
        return roomRepo.findRoomsByUserId(userId).stream()
            .map(r -> toRoomResponse(r, userId))
            .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // MEMBERSHIP
    // ═══════════════════════════════════════════════════════════

    @Override
    public RoomMemberResponse addMember(String roomId, String adminId, AddMemberRequest req, String token) {
        Room room = findRoom(roomId);
        assertAdmin(room, adminId);

        if (memberRepo.existsByRoom_RoomIdAndUserId(roomId, req.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
        }
        long currentCount = memberRepo.countByRoom_RoomId(roomId);
        if (currentCount >= room.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is at max capacity");
        }

        RoomMemberRole role = "ADMIN".equalsIgnoreCase(req.getRole())
            ? RoomMemberRole.ADMIN : RoomMemberRole.MEMBER;

        RoomMember member = addMemberInternal(room, req.getUserId(), role);

        AuthServiceClient.UserProfileDto profile = authClient.getUserById(req.getUserId(), token);
        return toMemberResponse(member, profile);
    }

    @Override
    public void removeMember(String roomId, String adminId, String targetUserId) {
        Room room = findRoom(roomId);
        assertAdmin(room, adminId);
        if (targetUserId.equals(room.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove the room creator");
        }
        assertMemberExists(roomId, targetUserId);
        memberRepo.deleteByRoom_RoomIdAndUserId(roomId, targetUserId);
    }

    @Override
    public void leaveRoom(String roomId, String userId) {
        Room room = findRoom(roomId);
        assertMemberExists(roomId, userId);
        if (userId.equals(room.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Room creator cannot leave. Transfer ownership or delete the room.");
        }
        memberRepo.deleteByRoom_RoomIdAndUserId(roomId, userId);
    }

    @Override
    public RoomResponse joinByInviteLink(String inviteLink, String userId) {
        Room room = roomRepo.findByInviteLink(inviteLink)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invite link"));

        if (room.getType() == RoomType.DM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot join a DM via invite link");
        }
        if (memberRepo.existsByRoom_RoomIdAndUserId(room.getRoomId(), userId)) {
            return toRoomResponse(room, userId); // already a member — idempotent
        }
        long count = memberRepo.countByRoom_RoomId(room.getRoomId());
        if (count >= room.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is at max capacity");
        }
        addMemberInternal(room, userId, RoomMemberRole.MEMBER);
        return toRoomResponse(room, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(String roomId, String requesterId, String token) {
        findRoom(roomId);
        assertMemberExists(roomId, requesterId);
        return memberRepo.findByRoom_RoomId(roomId).stream()
            .map(m -> {
                AuthServiceClient.UserProfileDto profile = authClient.getUserById(m.getUserId(), token);
                return toMemberResponse(m, profile);
            })
            .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ROOM ADMIN ACTIONS  §2.5
    // ═══════════════════════════════════════════════════════════

    @Override
    public RoomMemberResponse changeRole(String roomId, String adminId, String targetUserId, String newRole) {
        findRoom(roomId);
        assertAdmin(findRoom(roomId), adminId);
        RoomMember member = findMember(roomId, targetUserId);
        member.setRole("ADMIN".equalsIgnoreCase(newRole) ? RoomMemberRole.ADMIN : RoomMemberRole.MEMBER);
        memberRepo.save(member);
        return toMemberResponse(member, null);
    }

    @Override
    public RoomMemberResponse toggleMute(String roomId, String adminId, String targetUserId) {
        findRoom(roomId);
        assertAdmin(findRoom(roomId), adminId);
        RoomMember member = findMember(roomId, targetUserId);
        member.setMuted(!member.isMuted());
        memberRepo.save(member);
        return toMemberResponse(member, null);
    }

    // ═══════════════════════════════════════════════════════════
    // MESSAGES  §2.4 + §2.5
    // ═══════════════════════════════════════════════════════════

    @Override
    public MessageResponse sendMessage(String roomId, String senderId, SendMessageRequest req) {
        Room room = findRoom(roomId);
        assertMember(room, senderId);

        RoomMember sender = findMember(roomId, senderId);
        if (sender.isMuted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are muted in this room");
        }

        Message msg = Message.builder()
            .roomId(roomId)
            .senderId(senderId)
            .content(req.getContent())
            .type(req.getType() != null ? req.getType() : "TEXT")
            .fileUrl(req.getFileUrl())
            .replyToId(req.getReplyToId())
            .build();
        messageRepo.save(msg);

        // Update room's lastMessageAt for sorting
        room.setLastMessageAt(LocalDateTime.now());
        roomRepo.save(room);

        return toMessageResponse(msg, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(String roomId, String requesterId, int page, int size) {
        findRoom(roomId);
        assertMemberExists(roomId, requesterId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return messageRepo.findByRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(roomId, pageable)
            .map(m -> toMessageResponse(m, null));
    }

    @Override
    public void deleteMessage(String roomId, String requesterId, String messageId) {
        findRoom(roomId);
        assertMemberExists(roomId, requesterId);

        Message msg = messageRepo.findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // Own message OR room admin can delete
        boolean isRoomAdmin = findMember(roomId, requesterId).getRole() == RoomMemberRole.ADMIN;
        if (!msg.getSenderId().equals(requesterId) && !isRoomAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this message");
        }
        msg.setDeleted(true);
        msg.setUpdatedAt(LocalDateTime.now());
        messageRepo.save(msg);
    }

    @Override
    public void clearHistory(String roomId, String adminId) {
        findRoom(roomId);
        assertAdmin(findRoom(roomId), adminId);
        messageRepo.softDeleteAllByRoomId(roomId);
    }

    @Override
    public MessageResponse pinMessage(String roomId, String adminId, String messageId) {
        findRoom(roomId);
        assertAdmin(findRoom(roomId), adminId);

        // Unpin any existing pinned message first
        messageRepo.findByRoomIdAndIsPinnedTrue(roomId)
            .ifPresent(m -> { m.setPinned(false); messageRepo.save(m); });

        Message msg = messageRepo.findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        msg.setPinned(true);
        return toMessageResponse(messageRepo.save(msg), null);
    }

    @Override
    public void unpinMessage(String roomId, String adminId) {
        findRoom(roomId);
        assertAdmin(findRoom(roomId), adminId);
        messageRepo.findByRoomIdAndIsPinnedTrue(roomId)
            .ifPresent(m -> { m.setPinned(false); messageRepo.save(m); });
    }

    @Override
    public void markAsRead(String roomId, String userId) {
        RoomMember member = findMember(roomId, userId);
        member.setLastReadAt(LocalDateTime.now());
        memberRepo.save(member);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private Room findRoom(String roomId) {
        return roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    private RoomMember findMember(String roomId, String userId) {
        return memberRepo.findByRoom_RoomIdAndUserId(roomId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this room"));
    }

    private void assertMemberExists(String roomId, String userId) {
        if (!memberRepo.existsByRoom_RoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this room");
        }
    }

    private void assertMember(Room room, String userId) {
        assertMemberExists(room.getRoomId(), userId);
    }

    private void assertAdmin(Room room, String userId) {
        RoomMember m = findMember(room.getRoomId(), userId);
        if (m.getRole() != RoomMemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Room Admin role required");
        }
    }

    private RoomMember addMemberInternal(Room room, String userId, RoomMemberRole role) {
        RoomMember member = RoomMember.builder()
            .room(room)
            .userId(userId)
            .role(role)
            .build();
        return memberRepo.save(member);
    }

    // ── Mappers ──────────────────────────────────────────────────

    private RoomResponse toRoomResponse(Room r, String requesterId) {
        long unread = 0;
        String currentRole = "MEMBER";
        try {
            RoomMember m = findMember(r.getRoomId(), requesterId);
            currentRole = m.getRole().name();
            if (m.getLastReadAt() != null) {
                unread = messageRepo.countUnread(r.getRoomId(), requesterId);
            }
        } catch (Exception ignored) {}

        return RoomResponse.builder()
            .roomId(r.getRoomId())
            .name(r.getName())
            .description(r.getDescription())
            .type(r.getType().name())
            .avatarUrl(r.getAvatarUrl())
            .maxMembers(r.getMaxMembers())
            .memberCount((int) memberRepo.countByRoom_RoomId(r.getRoomId()))
            .createdBy(r.getCreatedBy())
            .inviteLink(r.getInviteLink())
            .lastMessageAt(r.getLastMessageAt())
            .createdAt(r.getCreatedAt())
            .unreadCount(unread)
            .currentUserRole(currentRole)
            .build();
    }

    private RoomMemberResponse toMemberResponse(RoomMember m,
                                                 AuthServiceClient.UserProfileDto profile) {
        return RoomMemberResponse.builder()
            .userId(m.getUserId())
            .username(profile != null ? profile.getUsername() : null)
            .fullName(profile != null ? profile.getFullName() : null)
            .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
            .status(profile != null ? profile.getStatus() : "OFFLINE")
            .role(m.getRole().name())
            .isMuted(m.isMuted())
            .joinedAt(m.getJoinedAt())
            .lastReadAt(m.getLastReadAt())
            .build();
    }

    private MessageResponse toMessageResponse(Message m,
                                               AuthServiceClient.UserProfileDto sender) {
        return MessageResponse.builder()
            .messageId(m.getMessageId())
            .roomId(m.getRoomId())
            .senderId(m.getSenderId())
            .senderName(sender != null ? sender.getFullName() : null)
            .senderAvatar(sender != null ? sender.getAvatarUrl() : null)
            .content(m.isDeleted() ? "[Message deleted]" : m.getContent())
            .type(m.getType())
            .fileUrl(m.getFileUrl())
            .isPinned(m.isPinned())
            .isDeleted(m.isDeleted())
            .replyToId(m.getReplyToId())
            .createdAt(m.getCreatedAt())
            .build();
    }
}
