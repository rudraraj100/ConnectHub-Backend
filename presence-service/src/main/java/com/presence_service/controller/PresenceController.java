package com.presence_service.controller;

import com.presence_service.dto.ApiResponse;
import com.presence_service.dto.PresenceRecord;
import com.presence_service.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PresenceController — all routes under /presence/**
 *
 * Identity: userId comes from the X-User-Id header injected by the API Gateway.
 * No JWT parsing here — gateway handles all auth.
 *
 * Endpoints:
 *   POST   /presence/heartbeat          — refresh online status (called every ~20s)
 *   PUT    /presence/status             — set AWAY or OFFLINE explicitly
 *   GET    /presence/{userId}           — get single user's presence
 *   POST   /presence/bulk              — get presence for a list of userIds
 */
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "User online status and last-seen tracking")
public class PresenceController {

    private final PresenceService presenceService;

    // ── Heartbeat (called by frontend every 20s while tab is open) ───────────

    @PostMapping("/heartbeat")
    @Operation(summary = "Refresh the caller's ONLINE status (heartbeat ping)")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @RequestHeader("X-User-Id") String userId) {

        presenceService.heartbeat(userId);
        return ResponseEntity.ok(ApiResponse.ok("Heartbeat received"));
    }

    // ── Explicit status update (AWAY on tab blur, OFFLINE on logout) ─────────

    @PutMapping("/status")
    @Operation(summary = "Explicitly set status: ONLINE | AWAY | OFFLINE")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String status) {

        String s = status.toUpperCase();
        if (!List.of("ONLINE", "AWAY", "OFFLINE").contains(s)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid status. Use ONLINE, AWAY or OFFLINE"));
        }
        presenceService.setStatus(userId, s);
        return ResponseEntity.ok(ApiResponse.ok("Status updated to " + s));
    }

    // ── Get single user presence ──────────────────────────────────────────────

    @GetMapping("/{userId}")
    @Operation(summary = "Get presence record for a single user")
    public ResponseEntity<ApiResponse<PresenceRecord>> getPresence(
            @PathVariable String userId) {

        PresenceRecord record = presenceService.getPresence(userId);
        return ResponseEntity.ok(ApiResponse.ok("Presence fetched", record));
    }

    // ── Bulk presence for contact list ────────────────────────────────────────

    @PostMapping("/bulk")
    @Operation(summary = "Get presence for multiple users at once (contact list)")
    public ResponseEntity<ApiResponse<Map<String, PresenceRecord>>> getBulkPresence(
            @RequestBody List<String> userIds) {

        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("userIds list must not be empty"));
        }
        Map<String, PresenceRecord> result = presenceService.getBulkPresence(userIds);
        return ResponseEntity.ok(ApiResponse.ok("Bulk presence fetched", result));
    }
}
