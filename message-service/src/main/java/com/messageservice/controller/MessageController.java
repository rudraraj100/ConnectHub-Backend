package com.messageservice.controller;

import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.ApiResponse;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
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
 * MessageResource — REST controller for all /messages endpoints.
 *
 * Security model (matching room-service pattern):
 *   - NO Spring Security in this service
 *   - JWT is validated by the API Gateway JwtGatewayFilter
 *   - Gateway injects X-User-Id header after validation
 *   - This controller reads X-User-Id from the header directly
 *
 * Case study §4.3 MessageResource:
 *   POST (send), GET (by id/room/before), PUT (edit), DELETE,
 *   GET (search/count/unread), PUT (status)
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message persistence, pagination, search and delivery status")
public class MessageController {

    private final MessageService messageService;

    // ── userId helper (identical to RoomController pattern) ──────────
    private String userId(HttpServletRequest req) {
        String uid = req.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header is required");
        }
        return uid;
    }

    // ── Send ─────────────────────────────────────────────────────────

    @PostMapping("/room/{roomId}")
    @Operation(summary = "Send a message to a room")
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable String roomId,
            HttpServletRequest req,
            @Valid @RequestBody SendMessageRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message sent",
                        messageService.sendMessage(roomId, userId(req), body)));
    }

    // ── Get by ID ────────────────────────────────────────────────────

    @GetMapping("/{messageId}")
    @Operation(summary = "Get a single message by its ID")
    public ResponseEntity<ApiResponse<MessageResponse>> getById(
            @PathVariable String messageId) {
        return messageService.getMessageById(messageId)
                .map(m -> ResponseEntity.ok(ApiResponse.ok("Message fetched", m)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    // ── Paginated Room History ────────────────────────────────────────

    @GetMapping("/room/{roomId}")
    @Operation(summary = "Get paginated message history for a room (newest first)")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getByRoom(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
                messageService.getMessagesByRoom(roomId, page, size)));
    }

    // ── Messages Before Timestamp ─────────────────────────────────────

    @GetMapping("/room/{roomId}/before")
    @Operation(summary = "Get messages before a timestamp (scroll-up loading)")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getBefore(
            @PathVariable String roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        return ResponseEntity.ok(ApiResponse.ok("Messages fetched",
                messageService.getMessagesBefore(roomId, before)));
    }

    // ── Edit ──────────────────────────────────────────────────────────

    @PutMapping("/{messageId}")
    @Operation(summary = "Edit a message (sender only) — broadcasts MESSAGE_EDIT STOMP event")
    public ResponseEntity<ApiResponse<MessageResponse>> edit(
            @PathVariable String messageId,
            HttpServletRequest req,
            @Valid @RequestBody EditMessageRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Message updated",
                messageService.editMessage(messageId, userId(req), body)));
    }

    // ── Soft Delete ───────────────────────────────────────────────────

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Soft-delete a message (sender only) — broadcasts MESSAGE_DELETE STOMP event")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String messageId,
            HttpServletRequest req) {
        messageService.deleteMessage(messageId, userId(req));
        return ResponseEntity.ok(ApiResponse.ok("Message deleted"));
    }

    // ── Search ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/search")
    @Operation(summary = "Full-text search messages within a room")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> search(
            @PathVariable String roomId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.ok("Search results",
                messageService.searchMessages(roomId, keyword)));
    }

    // ── Delivery Status ───────────────────────────────────────────────

    @PutMapping("/{messageId}/status")
    @Operation(summary = "Update delivery status: SENT | DELIVERED | READ")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String messageId,
            @RequestParam String status) {
        messageService.updateDeliveryStatus(messageId, status);
        return ResponseEntity.ok(ApiResponse.ok("Status updated"));
    }

    // ── Count ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/count")
    @Operation(summary = "Get total message count for a room")
    public ResponseEntity<ApiResponse<Long>> getCount(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.ok("Count fetched",
                messageService.getMessageCount(roomId)));
    }

    // ── Unread ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/unread")
    @Operation(summary = "Get unread messages since a timestamp (used for badge count)")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getUnread(
            @PathVariable String roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(ApiResponse.ok("Unread messages",
                messageService.getUnreadMessages(roomId, since)));
    }
}
