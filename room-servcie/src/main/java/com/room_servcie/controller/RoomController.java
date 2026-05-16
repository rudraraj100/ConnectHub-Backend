package com.room_servcie.controller;

import com.room_servcie.dto.request.*;
import com.room_servcie.dto.response.*;
import com.room_servcie.entity.RoomType;
import com.room_servcie.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 * RoomController handles all HTTP requests related to rooms and group chats.
 * It provides endpoints for creating rooms, managing members, and sending messages.
 */
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room/Channel management, membership and messages")
public class RoomController {

    private final RoomService roomService;

    /**
     * Extracts the user identity from the custom X-User-Id header.
     * This header is injected by the API Gateway after successful JWT validation.
     */
    private String userId(HttpServletRequest req) {
        String uid = req.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header is required");
        }
        return uid;
    }

    /**
     * Extracts the raw Authorization token if needed for downstream calls.
     */
    private String token(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    // ── Room CRUD ─────────────────────────────────────────────────

    /**
     * Creates a new room.
     * Logic: DM rooms are free for everyone, but GROUP rooms require a PREMIUM plan.
     */
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new GROUP room or DM",
               description = "DMs are free for all users. Group rooms require a PREMIUM subscription.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Room created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Group room requires PREMIUM plan", content = @Content)
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            HttpServletRequest req,
            @Valid @RequestBody CreateRoomRequest body) {

        String plan = req.getHeader("X-User-Plan");
        boolean isDm = body.getType() == RoomType.DM;

        if (!isDm && !"PREMIUM".equals(plan)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Creating group rooms requires a Premium subscription. Upgrade to continue.");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Room created", roomService.createRoom(userId(req), body)));
    }

    @GetMapping("/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get room details")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room details returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a room member", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Room not found", content = @Content)
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(
            @PathVariable String roomId, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Room fetched",
            roomService.getRoomById(roomId, userId(req))));
    }

    @PutMapping("/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update room settings (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody UpdateRoomRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Room updated",
            roomService.updateRoom(roomId, userId(req), body)));
    }

    @GetMapping("/my")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get all rooms the current user belongs to")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room list returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Rooms fetched",
            roomService.getMyRooms(userId(req))));
    }

    // ── Membership ────────────────────────────────────────────────

    @PostMapping("/{roomId}/members")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Add a member to the room (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Member added")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<RoomMemberResponse>> addMember(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody AddMemberRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Member added",
                roomService.addMember(roomId, userId(req), body, token(req))));
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove a member (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            HttpServletRequest req) {
        roomService.removeMember(roomId, userId(req), targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("Member removed"));
    }

    @DeleteMapping("/{roomId}/leave")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Leave a room")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Left room successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.leaveRoom(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Left room"));
    }

    @PostMapping("/join/{inviteLink}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Join a room via invite link")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Joined room")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invalid invite link", content = @Content)
    public ResponseEntity<ApiResponse<RoomResponse>> joinByInviteLink(
            @PathVariable String inviteLink, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Joined room",
            roomService.joinByInviteLink(inviteLink, userId(req))));
    }

    @GetMapping("/{roomId}/members")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get room members with roles")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member list returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a room member", content = @Content)
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @PathVariable String roomId, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Members fetched",
            roomService.getMembers(roomId, userId(req), token(req))));
    }

    // ── Room Admin Actions ─────────────────────────────────────────

    @PatchMapping("/{roomId}/members/{targetUserId}/role")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change a member's role (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<RoomMemberResponse>> changeRole(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            @RequestParam String role,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated",
            roomService.changeRole(roomId, userId(req), targetUserId, role)));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/mute")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Toggle mute for a member (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mute state toggled")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<RoomMemberResponse>> toggleMute(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Mute toggled",
            roomService.toggleMute(roomId, userId(req), targetUserId)));
    }

    // ── Messages ──────────────────────────────────────────────────

    @PostMapping("/{roomId}/messages")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Send a message to a room")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a room member", content = @Content)
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String roomId, HttpServletRequest req,
            @Valid @RequestBody SendMessageRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Message sent",
                roomService.sendMessage(roomId, userId(req), body)));
    }

    @GetMapping("/{roomId}/messages")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get paginated message history",
               description = "FREE users: last 100 messages. PREMIUM users: unlimited.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message page returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessages(
            @PathVariable String roomId, HttpServletRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
            roomService.getMessages(roomId, userId(req), page, size)));
    }

    @DeleteMapping("/{roomId}/messages/{messageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a message")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the sender or room admin", content = @Content)
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest req) {
        roomService.deleteMessage(roomId, userId(req), messageId);
        return ResponseEntity.ok(ApiResponse.ok("Message deleted"));
    }

    @DeleteMapping("/{roomId}/messages")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Clear room message history (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "History cleared")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.clearHistory(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("History cleared"));
    }

    @PatchMapping("/{roomId}/messages/{messageId}/pin")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Pin a message (PREMIUM + Room Admin only)",
               description = "Requires PREMIUM plan. Only one message can be pinned per room at a time.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message pinned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin or FREE plan", content = @Content)
    public ResponseEntity<ApiResponse<MessageResponse>> pinMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Message pinned",
            roomService.pinMessage(roomId, userId(req), messageId)));
    }

    @DeleteMapping("/{roomId}/messages/pin")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unpin the currently pinned message (Room Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message unpinned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the room admin", content = @Content)
    public ResponseEntity<ApiResponse<Void>> unpinMessage(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.unpinMessage(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Message unpinned"));
    }

    // ── Unread ────────────────────────────────────────────────────

    @PostMapping("/{roomId}/read")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark all messages in a room as read")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room marked as read")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.markAsRead(roomId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Marked as read"));
    }

    // ── Platform Admin ────────────────────────────────────────

    @GetMapping("/admin/all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List ALL rooms — platform admin only")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All rooms returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getAllRooms(HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("All rooms",
            roomService.getAllRooms(userId(req))));
    }

    @DeleteMapping("/admin/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Hard-delete any room — platform admin only")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Room deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    public ResponseEntity<ApiResponse<Void>> adminDeleteRoom(
            @PathVariable String roomId, HttpServletRequest req) {
        roomService.adminDeleteRoom(userId(req), roomId);
        return ResponseEntity.ok(ApiResponse.ok("Room deleted"));
    }
}
