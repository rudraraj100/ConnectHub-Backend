package com.room_servcie.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client to call message-service for pin/unpin sync.
 *
 * Why do we need this?
 * ─────────────────────────────────────────────────────
 * Room-service and message-service use SEPARATE databases
 * (connecthub_rooms and connecthub_messages).
 * When a room admin pins a message, room-service flips isPinned in its own
 * DB (connecthub_rooms.messages).  BUT the frontend loads message history via
 * message-service (connecthub_messages.messages), so isPinned stays false
 * there — the banner never reappears after page reload.
 *
 * This client calls the message-service's internal pin endpoints to keep both
 * DBs in sync.  The call is fire-and-forget (exceptions are swallowed); if
 * message-service is temporarily down the room-service pin still succeeds and
 * the banner will update on the next restart/sync.
 */
@Slf4j
@Component
public class MessageServiceClient {

    @Value("${message.service.url:http://localhost:8083}")
    private String messageServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Tells message-service to unpin all messages in the room, then pin
     * the given messageId.
     *
     * PATCH http://localhost:8083/messages/{messageId}/pin?roomId={roomId}
     */
    public void pinMessage(String roomId, String messageId) {
        try {
            String url = messageServiceUrl + "/messages/" + messageId + "/pin?roomId=" + roomId;
            restTemplate.exchange(url, HttpMethod.PATCH, null, Void.class);
            log.debug("[Pin] Synced pin for message {} in room {} to message-service", messageId, roomId);
        } catch (Exception e) {
            // Non-fatal — room-service pin succeeded; message-service will sync on restart
            log.warn("[Pin] Could not sync pin to message-service: {}", e.getMessage());
        }
    }

    /**
     * Tells message-service to unpin all messages in the room.
     *
     * DELETE http://localhost:8083/messages/room/{roomId}/pin
     */
    public void unpinMessage(String roomId) {
        try {
            String url = messageServiceUrl + "/messages/room/" + roomId + "/pin";
            restTemplate.delete(url);
            log.debug("[Pin] Synced unpin for room {} to message-service", roomId);
        } catch (Exception e) {
            log.warn("[Pin] Could not sync unpin to message-service: {}", e.getMessage());
        }
    }
}
