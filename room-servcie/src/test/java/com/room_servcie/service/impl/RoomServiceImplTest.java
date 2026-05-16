package com.room_servcie.service.impl;

import com.room_servcie.client.AuthServiceClient;
import com.room_servcie.client.MessageServiceClient;
import com.room_servcie.dto.request.AddMemberRequest;
import com.room_servcie.dto.request.CreateRoomRequest;
import com.room_servcie.dto.request.SendMessageRequest;
import com.room_servcie.dto.request.UpdateRoomRequest;
import com.room_servcie.dto.response.MessageResponse;
import com.room_servcie.dto.response.RoomResponse;
import com.room_servcie.entity.*;
import com.room_servcie.repository.MessageRepository;
import com.room_servcie.repository.RoomMemberRepository;
import com.room_servcie.repository.RoomRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl — unit tests")
class RoomServiceImplTest {

    @Mock RoomRepository       roomRepo;
    @Mock RoomMemberRepository memberRepo;
    @Mock MessageRepository    messageRepo;
    @Mock AuthServiceClient    authClient;
    @Mock MessageServiceClient messageClient;

    @InjectMocks RoomServiceImpl sut;

    private Room        room;
    private RoomMember  adminMember;
    private RoomMember  plainMember;
    private Message     msg;

    @BeforeEach
    void setUp() {
        room = Room.builder()
                .roomId("room-1").name("Test Room")
                .type(RoomType.GROUP).maxMembers(100)
                .createdBy("admin-user").inviteLink("invite123")
                .build();

        adminMember = RoomMember.builder()
                .memberId("m-1").room(room).userId("admin-user")
                .role(RoomMemberRole.ADMIN).joinedAt(LocalDateTime.now()).build();

        plainMember = RoomMember.builder()
                .memberId("m-2").room(room).userId("plain-user")
                .role(RoomMemberRole.MEMBER).joinedAt(LocalDateTime.now()).build();

        msg = Message.builder()
                .messageId("msg-1").roomId("room-1")
                .senderId("admin-user").content("Hello").type("TEXT")
                .isDeleted(false).isPinned(false).build();
    }

    // ── createRoom ────────────────────────────────────────────────────

    @Test
    @DisplayName("createRoom() GROUP room — saves room and adds creator as ADMIN")
    void createRoom_group_success() {
        when(roomRepo.save(any())).thenReturn(room);
        when(memberRepo.save(any())).thenReturn(adminMember);
        lenient().when(memberRepo.findByRoom_RoomIdAndUserId(any(), any()))
                .thenReturn(Optional.of(adminMember));
        lenient().when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        lenient().when(messageRepo.countUnread(any(), any())).thenReturn(0L);

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Test Room");
        req.setType(RoomType.GROUP);

        RoomResponse resp = sut.createRoom("admin-user", req);

        assertThat(resp).isNotNull();
        verify(roomRepo).save(any(Room.class));
    }

    @Test
    @DisplayName("createRoom() DM — throws 400 when targetUserId is missing")
    void createRoom_dm_missingTarget_throws400() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType(RoomType.DM);

        assertThatThrownBy(() -> sut.createRoom("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("targetUserId");
    }

    @Test
    @DisplayName("createRoom() DM — throws 409 when DM already exists")
    void createRoom_dm_alreadyExists_throws409() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setType(RoomType.DM);
        req.setTargetUserId("user-2");

        when(roomRepo.findDmBetween("user-1", "user-2")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> sut.createRoom("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DM already exists");
    }

    // ── getRoomById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getRoomById() — throws 404 when room not found")
    void getRoomById_notFound_throws404() {
        when(roomRepo.findById("bad-room")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getRoomById("bad-room", "admin-user"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Room not found");
    }

    @Test
    @DisplayName("getRoomById() — throws 403 when requester is not a member")
    void getRoomById_notMember_throws403() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> sut.getRoomById("room-1", "stranger"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── updateRoom ────────────────────────────────────────────────────

    @Test
    @DisplayName("updateRoom() — updates room fields when requester is admin")
    void updateRoom_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(roomRepo.save(any())).thenReturn(room);
        lenient().when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        lenient().when(messageRepo.countUnread(any(), any())).thenReturn(0L);

        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setName("New Name");
        req.setDescription("New Desc");

        RoomResponse resp = sut.updateRoom("room-1", "admin-user", req);

        assertThat(resp).isNotNull();
        assertThat(room.getName()).isEqualTo("New Name");
        verify(roomRepo).save(room);
    }

    @Test
    @DisplayName("updateRoom() — throws 403 when requester is not admin")
    void updateRoom_notAdmin_throws403() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "plain-user"))
                .thenReturn(Optional.of(plainMember));

        UpdateRoomRequest updateReq = new UpdateRoomRequest();
        assertThatThrownBy(() -> sut.updateRoom("room-1", "plain-user", updateReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin");
    }

    // ── getMyRooms ────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyRooms() — returns mapped list")
    void getMyRooms_returnsRooms() {
        when(roomRepo.findRoomsByUserId("admin-user")).thenReturn(List.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        lenient().when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        lenient().when(messageRepo.countUnread(any(), any())).thenReturn(0L);

        List<RoomResponse> result = sut.getMyRooms("admin-user");

        assertThat(result).hasSize(1);
    }

    // ── addMember ─────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember() — throws 409 when user already a member")
    void addMember_alreadyMember_throws409() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "new-user")).thenReturn(true);

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId("new-user");

        assertThatThrownBy(() -> sut.addMember("room-1", "admin-user", req, "token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("addMember() — throws 400 when room at max capacity")
    void addMember_atCapacity_throws400() {
        room.setMaxMembers(2);
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "new-user")).thenReturn(false);
        when(memberRepo.countByRoom_RoomId("room-1")).thenReturn(2L);

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId("new-user");

        assertThatThrownBy(() -> sut.addMember("room-1", "admin-user", req, "token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max capacity");
    }

    // ── removeMember ──────────────────────────────────────────────────

    @Test
    @DisplayName("removeMember() — throws 403 when trying to remove room creator")
    void removeMember_creator_throws403() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> sut.removeMember("room-1", "admin-user", "admin-user"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("creator");
    }

    @Test
    @DisplayName("removeMember() — succeeds for non-creator member")
    void removeMember_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "plain-user")).thenReturn(true);

        sut.removeMember("room-1", "admin-user", "plain-user");

        verify(memberRepo).deleteByRoom_RoomIdAndUserId("room-1", "plain-user");
    }

    // ── leaveRoom ─────────────────────────────────────────────────────

    @Test
    @DisplayName("leaveRoom() — throws 400 when creator tries to leave")
    void leaveRoom_creator_throws400() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);

        assertThatThrownBy(() -> sut.leaveRoom("room-1", "admin-user"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("creator cannot leave");
    }

    @Test
    @DisplayName("leaveRoom() — succeeds for non-creator member")
    void leaveRoom_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "plain-user")).thenReturn(true);

        sut.leaveRoom("room-1", "plain-user");

        verify(memberRepo).deleteByRoom_RoomIdAndUserId("room-1", "plain-user");
    }

    // ── joinByInviteLink ──────────────────────────────────────────────

    @Test
    @DisplayName("joinByInviteLink() — throws 404 for invalid link")
    void joinByInviteLink_invalidLink_throws404() {
        when(roomRepo.findByInviteLink("bad-link")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.joinByInviteLink("bad-link", "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid invite link");
    }

    @Test
    @DisplayName("joinByInviteLink() — throws 403 for DM rooms")
    void joinByInviteLink_dm_throws403() {
        room.setType(RoomType.DM);
        when(roomRepo.findByInviteLink("invite123")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> sut.joinByInviteLink("invite123", "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DM");
    }

    @Test
    @DisplayName("joinByInviteLink() — idempotent if already a member")
    void joinByInviteLink_alreadyMember_returnsRoom() {
        when(roomRepo.findByInviteLink("invite123")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "plain-user")).thenReturn(true);
        lenient().when(memberRepo.findByRoom_RoomIdAndUserId(any(), any()))
                .thenReturn(Optional.of(plainMember));
        lenient().when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        lenient().when(messageRepo.countUnread(any(), any())).thenReturn(0L);

        RoomResponse resp = sut.joinByInviteLink("invite123", "plain-user");

        assertThat(resp).isNotNull();
        verify(memberRepo, never()).save(any());
    }

    // ── changeRole ────────────────────────────────────────────────────

    @Test
    @DisplayName("changeRole() — promotes member to ADMIN")
    void changeRole_toAdmin_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "plain-user"))
                .thenReturn(Optional.of(plainMember));

        sut.changeRole("room-1", "admin-user", "plain-user", "ADMIN");

        assertThat(plainMember.getRole()).isEqualTo(RoomMemberRole.ADMIN);
        verify(memberRepo).save(plainMember);
    }

    @Test
    @DisplayName("changeRole() — demotes ADMIN to MEMBER")
    void changeRole_toMember_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        RoomMember secondAdmin = RoomMember.builder()
                .memberId("m-3").room(room).userId("second-admin")
                .role(RoomMemberRole.ADMIN).joinedAt(LocalDateTime.now()).build();
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "second-admin"))
                .thenReturn(Optional.of(secondAdmin));

        sut.changeRole("room-1", "admin-user", "second-admin", "MEMBER");

        assertThat(secondAdmin.getRole()).isEqualTo(RoomMemberRole.MEMBER);
    }

    // ── toggleMute ────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleMute() — mutes an unmuted member")
    void toggleMute_mutes() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "plain-user"))
                .thenReturn(Optional.of(plainMember));

        sut.toggleMute("room-1", "admin-user", "plain-user");

        assertThat(plainMember.isMuted()).isTrue();
        verify(memberRepo).save(plainMember);
    }

    // ── sendMessage ───────────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage() — succeeds for unmuted member")
    void sendMessage_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        when(messageRepo.save(any())).thenReturn(msg);
        when(roomRepo.save(any())).thenReturn(room);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hello");
        req.setType("TEXT");

        MessageResponse resp = sut.sendMessage("room-1", "admin-user", req);

        assertThat(resp).isNotNull();
        verify(messageRepo).save(any(Message.class));
    }

    @Test
    @DisplayName("sendMessage() — throws 403 when sender is muted")
    void sendMessage_mutedSender_throws403() {
        adminMember.setMuted(true);
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hello");

        assertThatThrownBy(() -> sut.sendMessage("room-1", "admin-user", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("muted");
    }

    // ── deleteMessage ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleteMessage() — owner can delete own message")
    void deleteMessage_byOwner_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);
        when(messageRepo.findById("msg-1")).thenReturn(Optional.of(msg));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));

        sut.deleteMessage("room-1", "admin-user", "msg-1");

        assertThat(msg.isDeleted()).isTrue();
        verify(messageRepo).save(msg);
    }

    @Test
    @DisplayName("deleteMessage() — throws 403 for non-owner non-admin")
    void deleteMessage_notOwnerNotAdmin_throws403() {
        msg.setSenderId("someone-else");
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "plain-user")).thenReturn(true);
        when(messageRepo.findById("msg-1")).thenReturn(Optional.of(msg));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "plain-user"))
                .thenReturn(Optional.of(plainMember));

        assertThatThrownBy(() -> sut.deleteMessage("room-1", "plain-user", "msg-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("authorized");
    }

    @Test
    @DisplayName("deleteMessage() — throws 404 when message not found")
    void deleteMessage_notFound_throws404() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);
        when(messageRepo.findById("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.deleteMessage("room-1", "admin-user", "bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── clearHistory ──────────────────────────────────────────────────

    @Test
    @DisplayName("clearHistory() — admin can clear all messages")
    void clearHistory_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));

        sut.clearHistory("room-1", "admin-user");

        verify(messageRepo).softDeleteAllByRoomId("room-1");
    }

    // ── pinMessage / unpinMessage ─────────────────────────────────────

    @Test
    @DisplayName("pinMessage() — unpins existing and pins new message")
    void pinMessage_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        Message alreadyPinned = Message.builder().messageId("old").roomId("room-1")
                .senderId("x").isPinned(true).isDeleted(false).build();
        when(messageRepo.findByRoomIdAndIsPinnedTrue("room-1")).thenReturn(Optional.of(alreadyPinned));
        when(messageRepo.findById("msg-1")).thenReturn(Optional.of(msg));
        when(messageRepo.save(any())).thenReturn(msg);

        sut.pinMessage("room-1", "admin-user", "msg-1");

        assertThat(alreadyPinned.isPinned()).isFalse();
        assertThat(msg.isPinned()).isTrue();
        verify(messageClient).pinMessage("room-1", "msg-1");
    }

    @Test
    @DisplayName("unpinMessage() — clears pinned message and syncs to message-service")
    void unpinMessage_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-user"))
                .thenReturn(Optional.of(adminMember));
        msg.setPinned(true);
        when(messageRepo.findByRoomIdAndIsPinnedTrue("room-1")).thenReturn(Optional.of(msg));

        sut.unpinMessage("room-1", "admin-user");

        assertThat(msg.isPinned()).isFalse();
        verify(messageClient).unpinMessage("room-1");
    }

    // ── markAsRead ────────────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead() — updates lastReadAt for member")
    void markAsRead_success() {
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "plain-user"))
                .thenReturn(Optional.of(plainMember));

        sut.markAsRead("room-1", "plain-user");

        assertThat(plainMember.getLastReadAt()).isNotNull();
        verify(memberRepo).save(plainMember);
    }

    // ── getMessages ───────────────────────────────────────────────────

    @Test
    @DisplayName("getMessages() — returns paginated results")
    void getMessages_returnsPaginatedResults() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-user")).thenReturn(true);
        Page<Message> page = new PageImpl<>(List.of(msg));
        when(messageRepo.findByRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("room-1"), any(Pageable.class)))
                .thenReturn(page);

        var result = sut.getMessages("room-1", "admin-user", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── getAllRooms / adminDeleteRoom ─────────────────────────────────

    @Test
    @DisplayName("getAllRooms() — returns all rooms")
    void getAllRooms_returnsAll() {
        when(roomRepo.findAll()).thenReturn(List.of(room));
        lenient().when(memberRepo.findByRoom_RoomIdAndUserId(any(), any()))
                .thenReturn(Optional.of(adminMember));
        lenient().when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        lenient().when(messageRepo.countUnread(any(), any())).thenReturn(0L);

        List<RoomResponse> result = sut.getAllRooms("admin-user");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("adminDeleteRoom() — deletes room and all its members")
    void adminDeleteRoom_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoom_RoomId("room-1")).thenReturn(List.of(adminMember, plainMember));

        sut.adminDeleteRoom("admin-user", "room-1");

        verify(memberRepo).deleteAll(anyList());
        verify(roomRepo).delete(room);
    }

    @Test
    @DisplayName("adminDeleteRoom() — throws 404 when room not found")
    void adminDeleteRoom_notFound_throws404() {
        when(roomRepo.findById("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.adminDeleteRoom("admin-user", "bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Room not found");
    }
}
