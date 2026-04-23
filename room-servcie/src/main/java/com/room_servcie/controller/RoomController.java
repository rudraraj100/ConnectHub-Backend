package com.room_servcie.controller;

import com.room_servcie.dto.request.*;
import com.room_servcie.dto.response.*;
import com.room_servcie.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * RoomController — zero Spring Security.
 *
 * Pattern identical to the friend's BookingController:
 *   - No authentication framework
 *   - userId is read from the X-User-Id header which the API Gateway's
 *     JwtGatewayFilter injects after validating the JWT signature.
 *     The client never sends this header directly — the gateway strips
 *     any client-supplied value and injects one from the validated token.
 *   - Network isolation prevents direct calls to port 8082
 */
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room/Channel management, membership and messages")
public class RoomController {

    private final RoomService roomService;

    // ── userId helper — reads the header the frontend always sends ──
    private String userId(HttpServletRequest req) {
        String uid = req.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header is required");
        }
        return uid;
    }

    private String token(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    // ── Room CRUD ─────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new GROUP room or DM")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            HttpServletRequest req,
            @Valid @RequestBody CreateRoomRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Room created", roomService.createRoom(userId(req), body)));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get room details")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(
            @PathVariable String roomId, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Room fetched",
            roomService.getRoomById(roomId, userId(req))));
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update room settings (Room Admin only)")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody UpdateRoomRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Room updated",
            roomService.updateRoom(roomId, userId(req), body)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get all rooms the current user belongs to")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Rooms fetched",
            roomService.getMyRooms(userId(req))));
    }

    // ── Membership ────────────────────────────────────────────────

    @PostMapping("/{roomId}/members")
    @Operation(summary = "Add a member to the room (Room Admin only)")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> addMember(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody AddMemberRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Member added",
                roomService.addMember(roomId, userId(req), body, token(req))));
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    @Operation(summary = "Remove a member (Room Admin only)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            HttpServletRequest req) {
        roomService.removeMember(roomId, userId(req), targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("Member removed"));
    }

    @DeleteMapping("/{roomId}/leave")
    @Operation(summary = "Leave a room")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.leaveRoom(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Left room"));
    }

    @PostMapping("/join/{inviteLink}")
    @Operation(summary = "Join a room via invite link")
    public ResponseEntity<ApiResponse<RoomResponse>> joinByInviteLink(
            @PathVariable String inviteLink, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Joined room",
            roomService.joinByInviteLink(inviteLink, userId(req))));
    }

    @GetMapping("/{roomId}/members")
    @Operation(summary = "Get room members with roles")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @PathVariable String roomId, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Members fetched",
            roomService.getMembers(roomId, userId(req), token(req))));
    }

    // ── Room Admin Actions ─────────────────────────────────────────

    @PatchMapping("/{roomId}/members/{targetUserId}/role")
    @Operation(summary = "Change a member's role (Room Admin only)")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> changeRole(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            @RequestParam String role,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated",
            roomService.changeRole(roomId, userId(req), targetUserId, role)));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/mute")
    @Operation(summary = "Toggle mute for a member (Room Admin only)")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> toggleMute(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Mute toggled",
            roomService.toggleMute(roomId, userId(req), targetUserId)));
    }

    // ── Messages ──────────────────────────────────────────────────

    @PostMapping("/{roomId}/messages")
    @Operation(summary = "Send a message to a room")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody SendMessageRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Message sent",
                roomService.sendMessage(roomId, userId(req), body)));
    }

    @GetMapping("/{roomId}/messages")
    @Operation(summary = "Get paginated message history")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessages(
            @PathVariable String roomId, HttpServletRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
            roomService.getMessages(roomId, userId(req), page, size)));
    }

    @DeleteMapping("/{roomId}/messages/{messageId}")
    @Operation(summary = "Delete a message")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest req) {
        roomService.deleteMessage(roomId, userId(req), messageId);
        return ResponseEntity.ok(ApiResponse.ok("Message deleted"));
    }

    @DeleteMapping("/{roomId}/messages")
    @Operation(summary = "Clear room message history (Room Admin only)")
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.clearHistory(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("History cleared"));
    }

    @PatchMapping("/{roomId}/messages/{messageId}/pin")
    @Operation(summary = "Pin a message (Room Admin only)")
    public ResponseEntity<ApiResponse<MessageResponse>> pinMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Message pinned",
            roomService.pinMessage(roomId, userId(req), messageId)));
    }

    @DeleteMapping("/{roomId}/messages/pin")
    @Operation(summary = "Unpin pinned message (Room Admin only)")
    public ResponseEntity<ApiResponse<Void>> unpinMessage(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.unpinMessage(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Message unpinned"));
    }

    // ── Unread ────────────────────────────────────────────────────

    @PostMapping("/{roomId}/read")
    @Operation(summary = "Mark room as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.markAsRead(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Marked as read"));
    }
}
