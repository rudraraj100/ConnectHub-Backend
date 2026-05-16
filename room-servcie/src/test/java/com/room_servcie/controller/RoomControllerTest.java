package com.room_servcie.controller;

import com.room_servcie.dto.request.CreateRoomRequest;
import com.room_servcie.dto.response.ApiResponse;
import com.room_servcie.dto.response.RoomResponse;
import com.room_servcie.entity.RoomType;
import com.room_servcie.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController - unit tests")
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RoomController sut;

    @Test
    @DisplayName("createRoom() succeeds for DM regardless of plan")
    void createRoom_dmSuccess() {
        when(request.getHeader("X-User-Id")).thenReturn("u1");
        when(request.getHeader("X-User-Plan")).thenReturn("FREE");
        
        CreateRoomRequest body = new CreateRoomRequest();
        body.setType(RoomType.DM);
        
        when(roomService.createRoom(eq("u1"), any())).thenReturn(new RoomResponse());

        ResponseEntity<ApiResponse<RoomResponse>> resp = sut.createRoom(request, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(roomService).createRoom(eq("u1"), any());
    }

    @Test
    @DisplayName("createRoom() fails for GROUP if user is not PREMIUM")
    void createRoom_groupFailsForFreeUser() {
        when(request.getHeader("X-User-Plan")).thenReturn("FREE");
        
        CreateRoomRequest body = new CreateRoomRequest();
        body.setType(RoomType.GROUP);

        assertThatThrownBy(() -> sut.createRoom(request, body))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Premium subscription");
    }

    @Test
    @DisplayName("getRoom() returns 200 OK")
    void getRoom_returnsOk() {
        when(request.getHeader("X-User-Id")).thenReturn("u1");
        when(roomService.getRoomById("r1", "u1")).thenReturn(new RoomResponse());

        ResponseEntity<ApiResponse<RoomResponse>> resp = sut.getRoom("r1", request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getMyRooms() calls service")
    void getMyRooms_callsService() {
        when(request.getHeader("X-User-Id")).thenReturn("u1");
        when(roomService.getMyRooms("u1")).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<RoomResponse>>> resp = sut.getMyRooms(request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(roomService).getMyRooms("u1");
    }

    @Test
    @DisplayName("userId() helper throws 401 if header missing")
    void userId_missingHeader_throws401() {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        
        assertThatThrownBy(() -> sut.getRoom("r1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("X-User-Id header is required");
    }
}
