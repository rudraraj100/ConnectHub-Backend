package com.messageservice.controller;

import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.ApiResponse;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MessageController handles the persistence and retrieval of chat messages.
 * It also manages delivery statuses (SENT, DELIVERED, READ) and bulk updates.
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message persistence, pagination, search and delivery status")
public class MessageController {

    private final MessageService messageService;

    // ── userId helper ─────────────────────────────────────────────────
    private String userId(HttpServletRequest req) {
        String uid = req.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header is required");
        }
        return uid;
    }

    /**
     * Saves a new message to the database for a specific room.
     */
    @PostMapping("/room/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Send a message to a room")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message persisted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable String roomId,
            HttpServletRequest req,
            @Valid @RequestBody SendMessageRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message sent",
                        messageService.sendMessage(roomId, userId(req), body)));
    }

    // ── Get by ID ─────────────────────────────────────────────────────

    @GetMapping("/{messageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a single message by its ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<MessageResponse>> getById(
            @PathVariable String messageId) {
        return messageService.getMessageById(messageId)
                .map(m -> ResponseEntity.ok(ApiResponse.ok("Message fetched", m)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    // ── Paginated Room History ─────────────────────────────────────────

    @GetMapping("/room/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get paginated message history for a room (newest first)",
               description = "FREE plan: capped at 100 messages. PREMIUM: unlimited history.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message page returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getByRoom(
            @PathVariable String roomId,
            @RequestHeader(value = "X-User-Plan", defaultValue = "FREE") String plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
                messageService.getMessagesByRoom(roomId, page, size, plan)));
    }

    // ── Messages Before Timestamp ──────────────────────────────────────

    @GetMapping("/room/{roomId}/before")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get messages before a timestamp (scroll-up / infinite scroll)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getBefore(
            @PathVariable String roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
                messageService.getMessagesBefore(roomId, before)));
    }

    // ── Edit ──────────────────────────────────────────────────────────

    @PutMapping("/{messageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Edit a message (sender only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the message sender", content = @Content)
    })
    public ResponseEntity<ApiResponse<MessageResponse>> edit(
            @PathVariable String messageId,
            HttpServletRequest req,
            @Valid @RequestBody EditMessageRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Message updated",
                messageService.editMessage(messageId, userId(req), body)));
    }

    // ── Soft Delete ───────────────────────────────────────────────────

    @DeleteMapping("/{messageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soft-delete a message (sender only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message soft-deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the message sender", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String messageId,
            HttpServletRequest req) {
        messageService.deleteMessage(messageId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Message deleted"));
    }

    // ── Search ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/search")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Full-text search messages within a room")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<MessageResponse>>> search(
            @PathVariable String roomId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.ok("Search results",
                messageService.searchMessages(roomId, keyword)));
    }

    // ── Single-message Delivery Status ────────────────────────────────

    @PutMapping("/{messageId}/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update delivery status for a single message: SENT | DELIVERED | READ")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String messageId,
            @RequestParam String status) {
        messageService.updateDeliveryStatus(messageId, status);
        return ResponseEntity.ok(ApiResponse.ok("Status updated"));
    }

    // ── Bug 1 fix: Bulk mark-all-read ────────────────────────────────

    /**
     * Marks ALL messages NOT from {@code readerId} in this room as READ in one query.
     *
     * Called by the WebSocket handler when a READ_RECEIPT arrives.
     * We pass the person who READ (readerId), and the DB marks every message
     * from everyone else (i.e., the messages they are reading) as READ.
     *
     * PUT /messages/room/{roomId}/mark-read?readerId=abc123
     */
    @PutMapping("/room/{roomId}/mark-read")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Bulk-mark all messages NOT from readerId in a room as READ",
               description = "Called by the WebSocket handler when a READ_RECEIPT event arrives.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "N messages marked as READ")
    })
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @PathVariable String roomId,
            @RequestParam String readerId) {
        int updated = messageService.markAllAsReadInRoom(roomId, readerId);
        return ResponseEntity.ok(ApiResponse.ok("Marked " + updated + " messages as READ"));
    }

    // ── Count ─────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/count")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get total message count for a room")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Count returned")
    })
    public ResponseEntity<ApiResponse<Long>> getCount(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.ok("Count fetched",
                messageService.getMessageCount(roomId)));
    }

    // ── Unread ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/unread")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get unread messages since a timestamp")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread messages returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getUnread(
            @PathVariable String roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(ApiResponse.ok("Unread messages",
                messageService.getUnreadMessages(roomId, since)));
    }

    // ── Pin (PREMIUM feature) ───────────────────────────────────────

    /**
     * PATCH /messages/{messageId}/pin
     * Called internally by room-service after it has validated that the
     * requester is a room admin with a PREMIUM plan.
     * Unpins all existing pins in the room first, then pins this message.
     */
    @PatchMapping("/{messageId}/pin")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Pin a message in a room (called by room-service after admin + plan validation)",
               description = "Unpins any previously pinned message in the room first. Called internally.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message pinned")
    })
    public ResponseEntity<ApiResponse<Void>> pinMessage(
            @PathVariable String messageId,
            @RequestParam String roomId) {
        messageService.pinMessage(roomId, messageId);
        return ResponseEntity.ok(ApiResponse.ok("Message pinned"));
    }

    /**
     * DELETE /messages/room/{roomId}/pin
     * Unpins the currently-pinned message in a room.
     * Called by room-service unpinMessage().
     */
    @DeleteMapping("/room/{roomId}/pin")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unpin the pinned message in a room (called by room-service)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message unpinned")
    })
    public ResponseEntity<ApiResponse<Void>> unpinMessage(@PathVariable String roomId) {
        messageService.unpinMessage(roomId);
        return ResponseEntity.ok(ApiResponse.ok("Message unpinned"));
    }
}