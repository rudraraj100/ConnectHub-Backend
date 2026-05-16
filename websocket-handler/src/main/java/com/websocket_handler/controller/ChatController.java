package com.websocket_handler.controller;

import com.websocket_handler.handler.ChatWebSocketHandler;
import com.websocket_handler.payload.Payloads.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * ChatController handles incoming real-time messages via the STOMP protocol.
 * It maps specific 'destinations' (like /app/chat.send) to Java methods.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatWebSocketHandler handler;

    /**
     * Handles a new chat message sent from a user.
     * It extracts the sender's ID from the session and delegates to the logic handler.
     */
    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload ChatMessage payload,
                                  SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) { log.warn("[WS] /chat.send — no userId in session"); return; }
        payload.setSenderId(userId);
        handler.handleChatMessage(payload, userId);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingIndicator payload,
                             SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setSenderId(userId);
        handler.handleTypingIndicator(payload);
    }

    @MessageMapping("/chat.read")
    public void handleRead(@Payload ReadReceipt payload,
                           SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setReaderId(userId);
        handler.handleReadReceipt(payload);
    }

    @MessageMapping("/chat.react")
    public void handleReaction(@Payload Reaction payload,
                               SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setSenderId(userId);
        handler.handleReaction(payload);
    }

    @MessageMapping("/chat.edit")
    public void handleEdit(@Payload MessageEdit payload,
                           SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setEditorId(userId);
        String roomId = extractHeader(headerAccessor, "roomId");
        handler.handleMessageEdit(payload, roomId != null ? roomId : "");
    }

    @MessageMapping("/chat.delete")
    public void handleDelete(@Payload MessageDelete payload,
                             SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setDeleterId(userId);
        String roomId = extractHeader(headerAccessor, "roomId");
        handler.handleMessageDelete(payload, roomId != null ? roomId : "");
    }

    @MessageMapping("/chat.join")
    public void handleJoin(@Payload RoomJoin payload,
                           SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setUserId(userId);
        log.info("[WS] JOIN roomId={} userId={}", payload.getRoomId(), userId);
    }

    @MessageMapping("/chat.leave")
    public void handleLeave(@Payload RoomLeave payload,
                            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        if (userId == null) return;
        payload.setUserId(userId);
        log.info("[WS] LEAVE roomId={} userId={}", payload.getRoomId(), userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reads userId from the STOMP session attributes.
     * SessionEventListener stores it there on CONNECTED event.
     * This is the correct way to get CONNECT-frame headers in @MessageMapping.
     */
    private String extractUserId(SimpMessageHeaderAccessor sha) {
        // 1. Try session attributes first (set by SessionEventListener on connect)
        var attrs = sha.getSessionAttributes();
        if (attrs != null) {
            Object val = attrs.get("userId");
            if (val instanceof String s && !s.isBlank()) return s;
        }
        // 2. Fallback: native headers on the CONNECT frame forwarded into the message
        var native_ = sha.toNativeHeaderMap();
        if (native_ != null) {
            var vals = native_.get("X-User-Id");
            if (vals != null && !vals.isEmpty()) return vals.get(0);
        }
        return null;
    }

    private String extractHeader(SimpMessageHeaderAccessor sha, String name) {
        var native_ = sha.toNativeHeaderMap();
        if (native_ == null) return null;
        var vals = native_.get(name);
        return (vals != null && !vals.isEmpty()) ? vals.get(0) : null;
    }
}
