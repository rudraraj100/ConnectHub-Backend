package com.presence_service.service;

import com.presence_service.dto.PresenceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Presence logic backed entirely by Redis.
 *
 * Key schema: "presence:{userId}"
 * Value:      PresenceRecord JSON
 * TTL:        ${app.presence.ttl-seconds}  (default 35 s)
 *
 * If the key expires (no heartbeat), the user is implicitly OFFLINE.
 * getPresence() returns a synthetic OFFLINE record for missing keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private static final String PREFIX = "presence:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.presence.ttl-seconds:35}")
    private long ttlSeconds;

    // ── heartbeat ────────────────────────────────────────────────────────────

    @Override
    public void heartbeat(String userId) {
        if (userId == null || userId.isBlank()) return;

        PresenceRecord record = PresenceRecord.builder()
                .userId(userId)
                .status("ONLINE")
                .lastSeenAt(Instant.now())
                .build();

        redisTemplate.opsForValue().set(PREFIX + userId, record, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Heartbeat: {} → ONLINE (TTL {}s)", userId, ttlSeconds);
    }

    // ── setStatus ────────────────────────────────────────────────────────────

    @Override
    public void setStatus(String userId, String status) {
        if (userId == null || userId.isBlank()) return;

        String key = PREFIX + userId;

        if ("OFFLINE".equalsIgnoreCase(status)) {
            redisTemplate.delete(key);
            log.debug("Status: {} → OFFLINE (key deleted)", userId);
            return;
        }

        // AWAY: preserve remaining TTL so the key still auto-expires
        Long remainingTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long ttl = (remainingTtl != null && remainingTtl > 0) ? remainingTtl : ttlSeconds;

        PresenceRecord record = PresenceRecord.builder()
                .userId(userId)
                .status(status.toUpperCase())
                .lastSeenAt(Instant.now())
                .build();

        redisTemplate.opsForValue().set(key, record, ttl, TimeUnit.SECONDS);
        log.debug("Status: {} → {} (TTL {}s)", userId, status, ttl);
    }

    // ── getPresence ──────────────────────────────────────────────────────────

    @Override
    public PresenceRecord getPresence(String userId) {
        if (userId == null || userId.isBlank()) return offlineRecord(userId);

        Object raw = redisTemplate.opsForValue().get(PREFIX + userId);
        if (raw instanceof PresenceRecord record) {
            return record;
        }
        if (raw instanceof Map<?, ?> map) {
            // GenericJackson2JsonRedisSerializer may deserialize as LinkedHashMap
            // when the @class type hint doesn't resolve at runtime.
            return mapToRecord(userId, map);
        }
        // Key not in Redis (TTL expired) → user is OFFLINE
        return offlineRecord(userId);
    }

    // ── getBulkPresence ──────────────────────────────────────────────────────

    @Override
    public Map<String, PresenceRecord> getBulkPresence(List<String> userIds) {
        Map<String, PresenceRecord> result = new LinkedHashMap<>();
        for (String uid : userIds) {
            result.put(uid, getPresence(uid));
        }
        return result;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PresenceRecord offlineRecord(String userId) {
        return PresenceRecord.builder()
                .userId(userId)
                .status("OFFLINE")
                .lastSeenAt(null)
                .build();
    }

    /**
     * Converts a LinkedHashMap (fallback deserialization) to a PresenceRecord.
     *
     * FIX: Map<?,?> getOrDefault() rejects String default due to wildcard capture.
     *      Use map.get() + null check instead.
     *
     * Jackson serializes Instant as epoch-seconds decimal, NOT ISO string:
     *   {"status":"ONLINE","lastSeenAt":1714123456.123456789}
     */
    private PresenceRecord mapToRecord(String userId, Map<?, ?> map) {
        // Safe wildcard-friendly extraction (getOrDefault fails with Map<?,?>)
        Object statusRaw   = map.get("status");
        String status      = (statusRaw != null) ? statusRaw.toString() : "OFFLINE";
        Object lastSeenRaw = map.get("lastSeenAt");

        Instant lastSeen = null;
        if (lastSeenRaw != null) {
            try {
                // RedisConfig now stores Instant as ISO-8601 string
                lastSeen = Instant.parse(lastSeenRaw.toString());
            } catch (Exception e) {
                // Fallback: old format stored as epoch decimal
                try {
                    double epochSeconds = Double.parseDouble(lastSeenRaw.toString());
                    long   epochSecLong = (long) epochSeconds;
                    long   nanoAdj      = Math.round((epochSeconds - epochSecLong) * 1_000_000_000L);
                    lastSeen = Instant.ofEpochSecond(epochSecLong, nanoAdj);
                } catch (Exception ignored) { /* leave null */ }
            }
        }

        return PresenceRecord.builder()
                .userId(userId)
                .status(status)
                .lastSeenAt(lastSeen)
                .build();
    }
}
