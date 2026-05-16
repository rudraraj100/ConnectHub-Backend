package com.websocket_handler.controller;

import com.websocket_handler.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PresenceController — REST endpoint to check live WebSocket session state.
 *
 * Note: This reflects in-memory session data only.
 * Persistent presence (Redis TTL) is managed by presence-service.
 */
@RestController
@RequestMapping("/ws/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final ChatWebSocketHandler handler;

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> isOnline(@PathVariable String userId) {
        boolean online = handler.isUserOnline(userId);
        int sessions   = handler.getUserSessionCount(userId);
        return ResponseEntity.ok(Map.of(
                "userId",       userId,
                "online",       online,
                "sessionCount", sessions
        ));
    }
}
