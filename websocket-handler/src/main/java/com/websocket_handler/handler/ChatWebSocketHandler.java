package com.websocket_handler.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.websocket_handler.config.AppConfig;
import com.websocket_handler.payload.Payloads.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatWebSocketHandler — wired to STOMP @MessageMapping controllers.
 *
 * Bug 1 fix (from previous refactor):
 *   saveMessage() now calls the correct endpoint:
 *   POST /messages/room/{roomId}  (was POST /messages → 404)
 *   handleReadReceipt() calls markAllMessagesReadInRoom() for bulk UPDATE.
 *
 * Bug 2 fix (this refactor):
 *   mediaUrl and mediaType are now threaded through the entire pipeline:
 *
 *   1. saveMessage() includes mediaUrl and mediaType in the REST body sent
 *      to message-service so they are persisted in the DB.
 *      Without this, the message was saved but mediaUrl was null, so a page
 *      refresh would show a text bubble instead of the image/video.
 *
 *   2. The broadcast envelope built in handleChatMessage() now includes
 *      mediaUrl and mediaType. The receiving Angular component reads
 *      frame.mediaUrl and frame.mediaType to decide whether to render an
 *      <img>, <video>, or plain text bubble. Without these fields, the
 *      receiver always saw a text bubble and had to refresh to see media.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler {

    // userId → set of active sessionIds (same user can have multiple tabs)
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messaging;
    private final RestTemplate          rest;
    private final AppConfig             cfg;
    private final ObjectMapper          objectMapper;

    /**
     * Safe text extraction from Jackson JsonNode.
     *
     * Problem: {@code NullNode.asText(defaultValue)} returns the string {@code "null"}
     * NOT the defaultValue — because {@code NullNode.asText()} returns "null" (non-empty),
     * so the default is never used. {@code MissingNode.asText(defaultValue)} correctly
     * returns the default. This helper handles both cases uniformly.
     *
     * @param node         the node to extract text from
     * @param defaultValue fallback when the node is missing, null JSON, blank, or "null"
     * @return the node's text value, or {@code defaultValue}
     */
    private static String safeText(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) return defaultValue;
        String val = node.asText();
        return (val == null || val.isBlank() || val.equals("null")) ? defaultValue : val;
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    public void afterConnectionEstablished(String userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.info("[WS] CONNECTED userId={} sessionId={}", userId, sessionId);
        updatePresence(userId, "ONLINE");
        broadcastPresence(userId, "ONLINE");
    }

    public void afterConnectionClosed(String userId, String sessionId) {
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                updatePresence(userId, "OFFLINE");
                broadcastPresence(userId, "OFFLINE");
                log.info("[WS] OFFLINE userId={} (all sessions closed)", userId);
            }
        }
    }

    // ── Message routing ───────────────────────────────────────────────────────

    /**
     * Routes a CHAT_MESSAGE:
     *  1. Saves to message-service (with mediaUrl/mediaType if present)
     *  2. Marks as DELIVERED
     *  3. Broadcasts to room (with mediaUrl/mediaType in the envelope)
     *  4. Notifies offline members via notification-service
     */
    public void handleChatMessage(ChatMessage payload, String senderUserId) {
        log.debug("[WS] CHAT_MESSAGE from {} to room {} (mediaUrl={})",
                senderUserId, payload.getRoomId(), payload.getMediaUrl());

        // 1. Persist via message-service
        String savedJson = saveMessage(senderUserId, payload);

        // 2. Parse the saved response and build broadcast envelope
        String broadcastPayload = savedJson;
        String messageId = null;
        // Declared outside try so it is in scope for notifyMembers() at step 5.
        // Initialised to senderUserId as a safe fallback when parsing fails.
        String senderName = senderUserId;

        try {
            JsonNode root = objectMapper.readTree(savedJson);
            // message-service wraps response: { "status":"ok", "data":{ ... } }
            JsonNode node = root.path("data").isMissingNode() ? root : root.path("data");

            messageId = safeText(node.path("messageId"), null);

            // Determine effective mediaUrl/mediaType — prefer what came back from DB,
            // fall back to what the client sent (in case message-service echoes differently)
            String effectiveMediaUrl  = safeText(node.path("mediaUrl"),  payload.getMediaUrl());
            String effectiveMediaType = safeText(node.path("mediaType"), payload.getMediaType());

            // senderName: prefer the name provided by the frontend (bypasses auth-service Feign),
            // then fall back to the message-service response, then senderId (UUID last resort).
            String frontendSenderName = safeText(null, payload.getSenderName()); // from STOMP payload
            senderName = frontendSenderName != null ? frontendSenderName
                                    : safeText(node.path("senderName"),
                                        safeText(node.path("senderUsername"), senderUserId));
            String senderUsername = safeText(node.path("senderUsername"), null);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type",           "CHAT_MESSAGE");
            envelope.put("messageId",      messageId);
            envelope.put("roomId",         safeText(node.path("roomId"), payload.getRoomId()));
            envelope.put("senderId",       safeText(node.path("senderId"), senderUserId));
            envelope.put("senderName",     senderName);
            envelope.put("senderUsername", senderUsername);   // @handle fallback for frontend
            envelope.put("content",        safeText(node.path("content"), payload.getContent()));
            envelope.put("sentAt",         safeText(node.path("sentAt"),
                                               java.time.Instant.now().toString()));
            // Bug 2 fix — include media fields in broadcast so receiver renders image/video
            envelope.put("mediaUrl",       effectiveMediaUrl);
            envelope.put("mediaType",      effectiveMediaType);
            envelope.put("deliveryStatus", "DELIVERED");

            broadcastPayload = toJson(envelope);

        } catch (Exception e) {
            log.warn("[WS] Could not parse saved message for broadcast envelope: {}", e.getMessage());
        }

        // 3. Persist DELIVERED so double-tick survives page refresh
        if (messageId != null && !messageId.isBlank()) {
            persistDeliveryStatus(messageId, "DELIVERED");
        } else {
            log.warn("[WS] saveMessage() returned no messageId — check message-service is running on {}",
                    cfg.messageServiceUrl);
        }

        // 4. Broadcast to all room subscribers
        broadcastToRoom(payload.getRoomId(), broadcastPayload);

        // 5. Notify online/offline members
        notifyMembers(payload.getRoomId(), senderUserId, senderName, payload.getContent());
    }

    /** Broadcasts typing indicator — no persistence */
    public void handleTypingIndicator(TypingIndicator payload) {
        Map<String, Object> event = Map.of(
                "type",     "TYPING_INDICATOR",
                "senderId", payload.getSenderId(),
                "roomId",   payload.getRoomId(),
                "isTyping", payload.isTyping()
        );
        broadcastToRoom(payload.getRoomId(), toJson(event));
    }

    /**
     * Marks all messages from the other sender as READ and broadcasts READ_RECEIPT.
     * (Bug 1 fix — bulk UPDATE instead of single-row update.)
     */
    public void handleReadReceipt(ReadReceipt payload) {
        markAllMessagesReadInRoom(payload.getRoomId(), payload.getUpToMessageId());

        Map<String, Object> event = Map.of(
                "type",          "READ_RECEIPT",
                "readerId",      payload.getReaderId(),
                "roomId",        payload.getRoomId(),
                "upToMessageId", payload.getUpToMessageId()
        );
        broadcastToRoom(payload.getRoomId(), toJson(event));
    }

    public void handleReaction(Reaction payload) {
        Map<String, Object> event = Map.of(
                "type",      "REACTION",
                "senderId",  payload.getSenderId(),
                "messageId", payload.getMessageId(),
                "emoji",     payload.getEmoji()
        );
        sendToUser(payload.getSenderId(), toJson(event));
    }

    public void handleMessageEdit(MessageEdit payload, String roomId) {
        Map<String, Object> event = Map.of(
                "type",       "MESSAGE_EDIT",
                "editorId",   payload.getEditorId(),
                "messageId",  payload.getMessageId(),
                "newContent", payload.getNewContent()
        );
        broadcastToRoom(roomId, toJson(event));
    }

    public void handleMessageDelete(MessageDelete payload, String roomId) {
        Map<String, Object> event = Map.of(
                "type",      "MESSAGE_DELETE",
                "deleterId", payload.getDeleterId(),
                "messageId", payload.getMessageId()
        );
        broadcastToRoom(roomId, toJson(event));
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    public void broadcastToRoom(String roomId, String payload) {
        messaging.convertAndSend("/topic/room/" + roomId, payload);
    }

    public void sendToUser(String userId, String payload) {
        messaging.convertAndSend("/topic/user/" + userId, payload);
    }

    public void broadcastPresence(String userId, String status) {
        Map<String, Object> event = Map.of(
                "type",   "PRESENCE_UPDATE",
                "userId", userId,
                "status", status
        );
        messaging.convertAndSend("/topic/presence", toJson(event));
    }

    public boolean isUserOnline(String userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** Returns the number of active WebSocket sessions for the given user (0 if offline). */
    public int getUserSessionCount(String userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }

    // ── REST calls to downstream services ────────────────────────────────────

    /**
     * Saves a message to message-service.
     *
     * Bug 1 fix: URL corrected to POST /messages/room/{roomId}
     * Bug 2 fix: mediaUrl and mediaType are now included in the request body
     *            so they are persisted in the DB and returned in the response.
     *            Without this, images/videos were uploaded to disk but their
     *            URLs were lost on page refresh.
     */
    private String saveMessage(String senderId, ChatMessage payload) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("senderId",         senderId);
            body.put("content",          payload.getContent());
            // Use the specific type (IMAGE/VIDEO/FILE) if media is present,
            // otherwise fall back to TEXT
            body.put("type",             payload.getMediaType() != null ? payload.getMediaType()
                                       : payload.getType()     != null ? payload.getType()
                                       : "TEXT");
            // Bug 2 fix — persist media fields
            body.put("mediaUrl",         payload.getMediaUrl());
            body.put("mediaType",        payload.getMediaType());
            body.put("replyToMessageId", payload.getReplyToId());
            // Pass senderName hint so message-service stores it directly and never
            // needs to call auth-service (which it can't reach without a JWT).
            body.put("senderName",       payload.getSenderName());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", senderId);

            // Bug 1 fix — correct endpoint (path variable, not body field)
            String url = cfg.messageServiceUrl + "/messages/room/" + payload.getRoomId();

            ResponseEntity<String> response = rest.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            log.debug("[WS] saveMessage → {} ({})", url, response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("[WS] Failed to save message to room {}: {}", payload.getRoomId(), e.getMessage());
            return toJson(Map.of("error", "Message save failed"));
        }
    }

    private void updatePresence(String userId, String status) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId);
            rest.exchange(
                    cfg.presenceServiceUrl + "/presence/status?status=" + status,
                    HttpMethod.PUT,
                    new HttpEntity<>(headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("[WS] Presence update failed for {}: {}", userId, e.getMessage());
        }
    }

    private void notifyMembers(String roomId, String senderId, String senderName, String content) {
        List<String> memberIds = new java.util.ArrayList<>();

        if (roomId != null && roomId.startsWith("dm_")) {
            // DM room ID format: "dm_{sortedUserId1}_{sortedUserId2}"
            // Both IDs are UUIDs (36 chars). We split on the first two underscores after "dm".
            // Format produced by frontend: `dm_${[userId, otherId].sort()[0]}_${...sort()[1]}`
            String withoutPrefix = roomId.substring(3); // strip "dm_"
            // UUIDs are 36 chars: 8-4-4-4-12. First UUID is positions 0..35, next starts at 37 (after '_')
            int separatorIdx = withoutPrefix.indexOf('_', 36); // look for '_' AFTER the first UUID
            if (separatorIdx > 0) {
                memberIds.add(withoutPrefix.substring(0, separatorIdx));
                memberIds.add(withoutPrefix.substring(separatorIdx + 1));
            } else {
                // Fallback: simple split (works if UUIDs don't contain underscores, which they don't)
                String[] parts = withoutPrefix.split("_", 2);
                if (parts.length == 2) {
                    memberIds.add(parts[0]);
                    memberIds.add(parts[1]);
                }
            }
            log.debug("[WS] DM notify: room={} members={}", roomId, memberIds);
        } else {
            // Group room — fetch member list from room-service.
            // X-User-Id is required so room-service can authorise the request.
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-User-Id", senderId);
                ResponseEntity<String> membersResp = rest.exchange(
                        cfg.roomServiceUrl + "/rooms/" + roomId + "/members",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                JsonNode root    = objectMapper.readTree(membersResp.getBody());
                JsonNode members = root.path("data");
                if (members.isArray()) {
                    for (JsonNode member : members) {
                        String mid = member.path("userId").asText();
                        if (!mid.isBlank()) memberIds.add(mid);
                    }
                }
            } catch (Exception e) {
                log.warn("[WS] Could not fetch room members for notifications (room={}): {}", roomId, e.getMessage());
            }
        }

        String preview = content != null && content.length() > 80
                ? content.substring(0, 80) + "…" : content;

        for (String memberId : memberIds) {
            if (memberId.equals(senderId)) continue;

            if (isUserOnline(memberId)) {
                // Recipient is online — push a real-time WS event so the frontend
                // can update the unread badge, bell, and auto-discover DM contacts.
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type",       "NEW_MESSAGE");
                event.put("roomId",     roomId);
                event.put("senderId",   senderId);
                event.put("senderName", senderName != null ? senderName : senderId);  // display name for auto-discovery
                event.put("message",    preview);
                sendToUser(memberId, toJson(event));
            } else {
                // Recipient is offline — store in notification-service for bell/push
                sendNotification(memberId, senderId, roomId, content);
            }
        }
    }

    /** Single-message delivery status update — used for DELIVERED on send */
    private void persistDeliveryStatus(String messageId, String status) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(
                    cfg.messageServiceUrl + "/messages/" + messageId + "/status?status=" + status,
                    HttpMethod.PUT,
                    new HttpEntity<>(headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("[WS] Could not persist {} status for {}: {}", status, messageId, e.getMessage());
        }
    }

    /**
     * Bug 1 fix — bulk marks all of the sender's messages in the room as READ.
     * Resolves senderId from upToMessageId, then calls PUT /messages/room/{roomId}/mark-read.
     */
    private void markAllMessagesReadInRoom(String roomId, String upToMessageId) {
        String senderId = resolveSenderFromMessage(upToMessageId);
        if (senderId == null) {
            log.warn("[WS] Cannot resolve sender for messageId={} — read receipt not persisted", upToMessageId);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(
                    cfg.messageServiceUrl + "/messages/room/" + roomId
                            + "/mark-read?senderId=" + senderId,
                    HttpMethod.PUT,
                    new HttpEntity<>(headers),
                    Void.class
            );
            log.debug("[WS] Bulk READ persisted for room={} sender={}", roomId, senderId);
        } catch (Exception e) {
            log.warn("[WS] Could not persist bulk READ for room {}: {}", roomId, e.getMessage());
        }
    }

    private String resolveSenderFromMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        try {
            ResponseEntity<String> resp = rest.exchange(
                    cfg.messageServiceUrl + "/messages/" + messageId,
                    HttpMethod.GET, null, String.class
            );
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
            String id = data.path("senderId").asText(null);
            return (id != null && !id.isBlank()) ? id : null;
        } catch (Exception e) {
            log.debug("[WS] resolveSender failed for {}: {}", messageId, e.getMessage());
            return null;
        }
    }

    private void sendNotification(String recipientId, String actorId, String roomId, String content) {
        try {
            // BUGFIX: Map.of() throws NullPointerException if any value is null.
            // content is null for media-only messages (image/video/file with no caption).
            // roomId can also be null in edge cases. Use a HashMap which allows nulls,
            // and provide safe defaults so the notification-service @NotBlank constraints
            // on "type" and "title" are always satisfied.
            String safeContent = (content != null && !content.isBlank())
                    ? (content.length() > 80 ? content.substring(0, 80) + "…" : content)
                    : "New media message";
            String safeRoomId  = (roomId != null) ? roomId : "";

            Map<String, Object> body = new HashMap<>();
            body.put("recipientId", recipientId);
            body.put("actorId",     actorId);
            body.put("type",        "NEW_MESSAGE");
            body.put("title",       "New message");
            body.put("message",     safeContent);
            body.put("roomId",      safeRoomId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(
                    cfg.notificationServiceUrl + "/notifications/send",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("[WS] Could not send notification to {}: {}", recipientId, e.getMessage());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}