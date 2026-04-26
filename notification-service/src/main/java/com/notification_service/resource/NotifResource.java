package com.notification_service.resource;

import com.notification_service.dto.ApiResponse;
import com.notification_service.dto.NotificationDTO;
import com.notification_service.dto.SendNotificationRequest;
import com.notification_service.service.NotifService;
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
 * No security configuration in this service.
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotifResource {

    private final NotifService notifService;

    // ── GET /notifications/me ─────────────────────────────────────────────────
    /** Fetch all notifications for the logged-in user */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getByRecipient(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok("Notifications fetched", notifService.getByRecipient(userId)));
    }

    // ── GET /notifications/unread-count ───────────────────────────────────────
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok("Unread count", notifService.getUnreadCount(userId)));
    }

    // ── GET /notifications/all (admin / inter-service) ────────────────────────
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.ok("All notifications", notifService.getAll()));
    }

    // ── POST /notifications/send (inter-service) ──────────────────────────────
    /** Called by message-service / room-service to dispatch a notification */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationDTO>> send(
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationDTO dto = notifService.send(request);
        return ResponseEntity.ok(ApiResponse.ok("Notification sent", dto));
    }

    // ── POST /notifications/send-bulk ─────────────────────────────────────────
    @PostMapping("/send-bulk")
    public ResponseEntity<ApiResponse<Void>> sendBulk(
            @RequestParam List<String> recipientIds,
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam String message) {
        notifService.sendBulk(recipientIds, type, title, message);
        return ResponseEntity.ok(ApiResponse.ok("Bulk notifications sent"));
    }

    // ── PATCH /notifications/{id}/read ────────────────────────────────────────
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        notifService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read"));
    }

    // ── PATCH /notifications/read-all ─────────────────────────────────────────
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @RequestHeader("X-User-Id") String userId) {
        notifService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok("All marked as read"));
    }

    // ── DELETE /notifications/{id} ────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        notifService.deleteNotification(id);
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted"));
    }
}
