package com.websocket_handler.payload;

import lombok.Data;

/**
 * All STOMP WebSocket payload types — JSON frames exchanged between
 * frontend and websocket-handler.
 *
 * Frontend sends to /app/chat.send, /app/chat.typing, /app/chat.read
 * Server broadcasts to /topic/room/{roomId} or /topic/user/{userId}
 *
 * Bug 2 fix: Added mediaUrl and mediaType to ChatMessage.
 *   Previously, when uploadAndSend() routed through WebSocket, the media
 *   fields were stripped here and never reached message-service or the
 *   broadcast envelope. The receiving user's component only renders media
 *   if frame.mediaUrl is present — so they saw a text bubble with no image.
 */
public class Payloads {

    // ── Inbound: sent by client ──────────────────────────────────────

    @Data
    public static class ChatMessage {
        private String senderId;
        private String roomId;
        private String content;
        private String type;       // TEXT | IMAGE | VIDEO | FILE

        /**
         * Sender's display name — populated by the frontend from the logged-in
         * user's profile (fullName ?? username). This lets the WS handler
         * embed a proper name in the broadcast envelope and persist it to the
         * DB without making a service-to-service Feign call to auth-service
         * (which would require a JWT the message-service doesn't hold).
         */
        private String senderName;

        // Bug 2 fix — carry media fields through the WebSocket pipeline.
        private String mediaUrl;   // absolute URL, e.g. http://localhost:8080/media/view/abc.png
        private String mediaType;  // IMAGE | VIDEO | FILE

        private String replyToId;
    }

    @Data
    public static class TypingIndicator {
        private String senderId;
        private String roomId;
        private boolean isTyping;
    }

    @Data
    public static class ReadReceipt {
        private String readerId;
        private String roomId;
        private String upToMessageId;
    }

    @Data
    public static class Reaction {
        private String senderId;
        private String messageId;
        private String emoji;
    }

    @Data
    public static class MessageEdit {
        private String editorId;
        private String messageId;
        private String newContent;
    }

    @Data
    public static class MessageDelete {
        private String deleterId;
        private String messageId;
    }

    @Data
    public static class RoomJoin {
        private String userId;
        private String roomId;
    }

    @Data
    public static class RoomLeave {
        private String userId;
        private String roomId;
    }

    // ── Outbound: broadcast by server ────────────────────────────────

    @Data
    public static class PresenceUpdate {
        private String userId;
        private String status;          // ONLINE | AWAY | OFFLINE
        private String customMessage;
    }
}