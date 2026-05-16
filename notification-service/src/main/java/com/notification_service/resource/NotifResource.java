package com.notification_service.resource;

import com.notification_service.dto.ApiResponse;
import com.notification_service.dto.BroadcastRequest;
import com.notification_service.dto.NotificationDTO;
import com.notification_service.dto.SendNotificationRequest;
import com.notification_service.service.NotifService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NotifResource — REST controller for /notifications/**
 *
 * User identification comes from the X-User-Id header injected by the API Gateway.
 * No local Spring Security — auth is enforced at the gateway level.
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management: fetch, send, mark-read, delete and broadcast")
public class NotifResource {

    private final NotifService notifService;

    // ── GET /notifications/me ─────────────────────────────────────────────────

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get all notifications for the current user",
            description = "Returns all notification records for the authenticated user, newest first.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Notification list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getByRecipient(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok("Notifications fetched", notifService.getByRecipient(userId)));
    }

    // ── GET /notifications/unread-count ───────────────────────────────────────

    @GetMapping("/unread-count")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get unread notification count for the current user",
            description = "Returns the count of notifications where `read = false`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Unread count returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok("Unread count", notifService.getUnreadCount(userId)));
    }

    // ── GET /notifications/all (admin / inter-service) ────────────────────────

    @GetMapping("/all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List ALL notifications (platform admin / inter-service)",
            description = """
                    Returns every notification record across all users. Intended for the admin
                    dashboard or internal debugging. Does not require a specific userId.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "All notifications returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.ok("All notifications", notifService.getAll()));
    }

    // ── POST /notifications/send (inter-service) ──────────────────────────────

    @PostMapping("/send")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Send a single notification (inter-service endpoint)",
            description = """
                    Called by `message-service` or `room-service` (via RabbitMQ consumers or direct HTTP)
                    to dispatch a notification to a specific recipient.
                    
                    **Notification types:** `MESSAGE`, `MENTION`, `ROOM_INVITE`, `SYSTEM`
                    
                    A real-time WebSocket push is also sent to `/topic/notifications/{recipientId}`
                    if the recipient is currently connected.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Notification created and dispatched"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid request body", content = @Content)
    })
    public ResponseEntity<ApiResponse<NotificationDTO>> send(
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationDTO dto = notifService.send(request);
        return ResponseEntity.ok(ApiResponse.ok("Notification sent", dto));
    }

    // ── POST /notifications/send-bulk ─────────────────────────────────────────

    @PostMapping("/send-bulk")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Send the same notification to multiple recipients",
            description = "Dispatches identical title + message to all provided recipientIds in one call.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Bulk notifications sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "recipientIds is empty or missing", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> sendBulk(
            @Parameter(description = "List of recipient userIds", required = true)
            @RequestParam List<String> recipientIds,

            @Parameter(description = "Notification type: MESSAGE | MENTION | ROOM_INVITE | SYSTEM",
                    required = true)
            @RequestParam String type,

            @Parameter(description = "Notification title", required = true)
            @RequestParam String title,

            @Parameter(description = "Notification body text", required = true)
            @RequestParam String message) {
        notifService.sendBulk(recipientIds, type, title, message);
        return ResponseEntity.ok(ApiResponse.ok("Bulk notifications sent"));
    }

    // ── PATCH /notifications/{id}/read ────────────────────────────────────────

    @PatchMapping("/{id}/read")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Mark a single notification as read",
            description = "Sets `read = true` on the notification with the given ID.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Notification marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Notification not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(description = "Notification ID (auto-incremented Long)", required = true)
            @PathVariable Long id) {
        notifService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read"));
    }

    // ── PATCH /notifications/read-all ─────────────────────────────────────────

    @PatchMapping("/read-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Mark all notifications as read for the current user",
            description = "Bulk-updates every unread notification for the authenticated user to `read = true`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "All notifications marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        notifService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok("All marked as read"));
    }

    // ── DELETE /notifications/{id} ────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Delete a notification",
            description = "Hard-deletes the notification record. Irreversible.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Notification deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Notification not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Notification ID (auto-incremented Long)", required = true)
            @PathVariable Long id) {
        notifService.deleteNotification(id);
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted"));
    }

    // ── POST /notifications/broadcast (platform admin) ────────────────────────

    @PostMapping("/broadcast")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Broadcast a SYSTEM notification to multiple users (platform admin)",
            description = """
                    Sends a `SYSTEM` type notification to every userId in the `recipientIds` list.
                    
                    The admin dashboard pre-fetches the list of active userIds from `auth-service`
                    and passes them here — this service does not make a cross-service call itself.
                    
                    Each recipient will receive a real-time WebSocket push if they are online.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Broadcast sent (or no-op if recipientIds is empty)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid request body", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> broadcast(
            @RequestBody BroadcastRequest req) {
        if (req.getRecipientIds() == null || req.getRecipientIds().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("No recipients — nothing sent"));
        }
        notifService.sendBulk(req.getRecipientIds(), "SYSTEM", req.getTitle(), req.getMessage());
        log.info("[Admin] Broadcast sent to {} users — title='{}'",
                req.getRecipientIds().size(), req.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Broadcast sent to " + req.getRecipientIds().size() + " users"));
    }
}
