package com.websocket_handler.listener;

import com.websocket_handler.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;

/**
 * SessionEventListener — listens to Spring WebSocket lifecycle events.
 *
 * CRITICAL: We listen to SessionConnectEvent (client's CONNECT frame),
 * NOT SessionConnectedEvent (server's CONNECTED reply frame).
 *
 * SessionConnectedEvent wraps the server-sent CONNECTED frame which has
 * NO client headers — X-User-Id is never present there, so userId would
 * always be null and every @MessageMapping would exit early without broadcasting.
 *
 * SessionConnectEvent wraps the raw CONNECT frame from the client,
 * which carries the connectHeaders { "X-User-Id": "..." } the frontend sends.
 *
 * On CONNECT:     extract userId → store in session attributes → call afterConnectionEstablished
 * On DISCONNECT:  remove session from presence map → call afterConnectionClosed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventListener {

    private final ChatWebSocketHandler handler;

    /**
     * Fires when the client sends a STOMP CONNECT frame (before server replies CONNECTED).
     * This is the correct place to read client-supplied headers like X-User-Id.
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String userId    = extractUserId(sha);

        if (userId != null && !userId.isBlank()) {
            // Store in session attributes so every @MessageMapping can read it via
            // SimpMessageHeaderAccessor.getSessionAttributes().get("userId")
            Map<String, Object> attrs = sha.getSessionAttributes();
            if (attrs != null) {
                attrs.put("userId", userId);
            }
            handler.afterConnectionEstablished(userId, sessionId);
            log.info("[WS] CONNECT userId={} sessionId={}", userId, sessionId);
        } else {
            log.warn("[WS] CONNECT received but X-User-Id missing from STOMP headers. sessionId={}", sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();

        // Session attributes are still available at disconnect time
        String userId = null;
        Map<String, Object> attrs = sha.getSessionAttributes();
        if (attrs != null) {
            Object val = attrs.get("userId");
            if (val instanceof String s) userId = s;
        }
        if (userId == null) userId = extractUserId(sha); // fallback

        if (userId != null && !userId.isBlank()) {
            handler.afterConnectionClosed(userId, sessionId);
        } else {
            log.debug("[WS] Session disconnected but no userId found. sessionId={}", sessionId);
        }
    }

    private String extractUserId(StompHeaderAccessor sha) {
        // The frontend sends X-User-Id in STOMP connectHeaders
        Map<String, List<String>> nativeHeaders = sha.toNativeHeaderMap();
        if (nativeHeaders != null) {
            List<String> values = nativeHeaders.get("X-User-Id");
            if (values != null && !values.isEmpty() && !values.get(0).isBlank()) {
                return values.get(0);
            }
        }
        return null;
    }
}
