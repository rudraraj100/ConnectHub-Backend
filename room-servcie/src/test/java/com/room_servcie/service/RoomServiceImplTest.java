package com.room_servcie.service;

import com.room_servcie.client.AuthServiceClient;
import com.room_servcie.client.MessageServiceClient;
import com.room_servcie.dto.request.AddMemberRequest;
import com.room_servcie.dto.request.CreateRoomRequest;
import com.room_servcie.dto.response.RoomResponse;
import com.room_servcie.entity.*;
import com.room_servcie.repository.MessageRepository;
import com.room_servcie.repository.RoomMemberRepository;
import com.room_servcie.repository.RoomRepository;
import com.room_servcie.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

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

    private Room  groupRoom;
    private RoomMember adminMember;

    @BeforeEach
    void setUp() {
        groupRoom = Room.builder()
                .roomId("room-1")
                .name("Test Room")
                .type(RoomType.GROUP)
                .maxMembers(100)
                .createdBy("admin-1")
                .build();

        adminMember = RoomMember.builder()
                .memberId("mem-1")
                .room(groupRoom)
                .userId("admin-1")
                .role(RoomMemberRole.ADMIN)
                .build();
    }

    // ── createRoom ────────────────────────────────────────────────────

    @Test
    @DisplayName("createRoom() GROUP saves room and adds creator as ADMIN")
    void createRoom_group_success() {
        when(roomRepo.save(any())).thenReturn(groupRoom);
        when(memberRepo.save(any())).thenReturn(adminMember);
        // toRoomResponse calls countByRoom_RoomId and findByRoom_RoomIdAndUserId
        when(memberRepo.countByRoom_RoomId(any())).thenReturn(1L);
        // Use lenient — roomId resolved inside the service may differ from our fixed mock roomId
        lenient().when(memberRepo.findByRoom_RoomIdAndUserId(any(), eq("admin-1")))
                .thenReturn(Optional.of(adminMember));

        CreateRoomRequest req = new CreateRoomRequest();
        req.setName("Test Room");
        req.setType(RoomType.GROUP);
        req.setMaxMembers(100);

        RoomResponse resp = sut.createRoom("admin-1", req);

        verify(roomRepo).save(any(Room.class));
        verify(memberRepo).save(any(RoomMember.class)); // creator added as ADMIN
        assertThat(resp).isNotNull();
        assertThat(resp.getRoomId()).isEqualTo("room-1");
        assertThat(resp.getName()).isEqualTo("Test Room");
    }

    @Test
    @DisplayName("createRoom() DM throws 409 when DM already exists")
    void createRoom_dm_alreadyExists_throws409() {
        when(roomRepo.findDmBetween("user-a", "user-b")).thenReturn(Optional.of(groupRoom));

        CreateRoomRequest req = new CreateRoomRequest();
        req.setType(RoomType.DM);
        req.setTargetUserId("user-b");

        assertThatThrownBy(() -> sut.createRoom("user-a", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DM already exists");
    }

    // ── getRoomById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getRoomById() throws 404 when room not found")
    void getRoomById_notFound_throws404() {
        when(roomRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getRoomById("missing", "admin-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getRoomById() throws 403 when requester is not a member")
    void getRoomById_notMember_throws403() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(groupRoom));
        // stranger is not a member — existsByRoom_RoomIdAndUserId returns false
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "stranger")).thenReturn(false);

        assertThatThrownBy(() -> sut.getRoomById("room-1", "stranger"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("getRoomById() returns RoomResponse for a member")
    void getRoomById_success() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(groupRoom));
        // assertMember → assertMemberExists → existsByRoom_RoomIdAndUserId
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "admin-1")).thenReturn(true);
        when(memberRepo.countByRoom_RoomId("room-1")).thenReturn(1L);
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-1"))
                .thenReturn(Optional.of(adminMember));

        RoomResponse resp = sut.getRoomById("room-1", "admin-1");

        assertThat(resp.getRoomId()).isEqualTo("room-1");
        assertThat(resp.getName()).isEqualTo("Test Room");
    }

    // ── addMember ─────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember() throws 409 when user is already a member")
    void addMember_alreadyMember_throws409() {
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(groupRoom));
        // assertAdmin: findMember returns adminMember with ADMIN role
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-1"))
                .thenReturn(Optional.of(adminMember));
        // new-user is already a member
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "new-user")).thenReturn(true);

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId("new-user");
        req.setRole("MEMBER");

        assertThatThrownBy(() -> sut.addMember("room-1", "admin-1", req, "token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("addMember() throws 400 when room is at capacity")
    void addMember_atCapacity_throws400() {
        groupRoom.setMaxMembers(1);
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(groupRoom));
        when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-1"))
                .thenReturn(Optional.of(adminMember));
        when(memberRepo.existsByRoom_RoomIdAndUserId("room-1", "new-user")).thenReturn(false);
        when(memberRepo.countByRoom_RoomId("room-1")).thenReturn(1L); // at max

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId("new-user");
        req.setRole("MEMBER");

        assertThatThrownBy(() -> sut.addMember("room-1", "admin-1", req, "token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max capacity");
    }

    // ── getMyRooms ────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyRooms() returns list of rooms for user")
    void getMyRooms_returnsList() {
        when(roomRepo.findRoomsByUserId("admin-1")).thenReturn(List.of(groupRoom));
        when(memberRepo.countByRoom_RoomId("room-1")).thenReturn(1L);
        lenient().when(memberRepo.findByRoom_RoomIdAndUserId("room-1", "admin-1"))
                .thenReturn(Optional.of(adminMember));

        List<RoomResponse> result = sut.getMyRooms("admin-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Room");
    }
}
