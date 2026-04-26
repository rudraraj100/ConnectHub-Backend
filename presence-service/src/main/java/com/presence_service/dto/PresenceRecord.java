package com.presence_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Presence record stored in Redis and returned by the API.
 * status: ONLINE | AWAY | OFFLINE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceRecord {
    private String userId;
    private String status;       // ONLINE | AWAY | OFFLINE
    private Instant lastSeenAt;
}
