package com.presence_service.service;

import com.presence_service.dto.PresenceRecord;

import java.util.List;
import java.util.Map;

public interface PresenceService {

    /**
     * Called by the frontend heartbeat (every ~20 s).
     * Sets the user ONLINE and resets the Redis TTL.
     */
    void heartbeat(String userId);

    /**
     * Explicitly mark user AWAY or OFFLINE (e.g. on page unload / logout).
     */
    void setStatus(String userId, String status);

    /**
     * Get presence record for a single user.
     * Returns OFFLINE record if key is not found in Redis (TTL expired).
     */
    PresenceRecord getPresence(String userId);

    /**
     * Bulk fetch for a contact list — returns a map of userId → PresenceRecord.
     * Used by the frontend to populate all contact dots in one call.
     */
    Map<String, PresenceRecord> getBulkPresence(List<String> userIds);
}
