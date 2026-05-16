package com.presence_service.controller;

import com.presence_service.dto.ApiResponse;
import com.presence_service.dto.PresenceRecord;
import com.presence_service.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PresenceController manages real-time user status (ONLINE, AWAY, OFFLINE).
 * It receives 'heartbeat' pings from the frontend to keep users active.
 */
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "User online status and last-seen tracking")
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * Receives a 'heartbeat' from the frontend. 
     * As long as the user's browser tab is open, this is called every 20s to stay ONLINE.
     */
    @PostMapping("/heartbeat")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Refresh the caller's ONLINE status (heartbeat ping)",
               description = "Frontend sends this every 20 s. Redis TTL is 35 s — missing 2 pings marks the user OFFLINE.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Heartbeat received"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @RequestHeader("X-User-Id") String userId) {

        presenceService.heartbeat(userId);
        return ResponseEntity.ok(ApiResponse.ok("Heartbeat received"));
    }

    // ── Explicit status update (AWAY on tab blur, OFFLINE on logout) ─────────

    @PutMapping("/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Explicitly set status: ONLINE | AWAY | OFFLINE",
               description = "Call with `OFFLINE` on logout, `AWAY` on tab blur. Overwrites the Redis key immediately.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
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
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get presence record for a single user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presence record returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<PresenceRecord>> getPresence(
            @PathVariable String userId) {

        PresenceRecord record = presenceService.getPresence(userId);
        return ResponseEntity.ok(ApiResponse.ok("Presence fetched", record));
    }

    // ── Bulk presence for contact list ────────────────────────────────────────

    @PostMapping("/bulk")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get presence for multiple users at once (contact list / room member display)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presence map returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "userIds list is null or empty"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
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
