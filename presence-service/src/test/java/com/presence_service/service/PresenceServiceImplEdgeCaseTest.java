package com.presence_service.service;

import com.presence_service.dto.PresenceRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceServiceImpl — edge-case tests")
class PresenceServiceImplEdgeCaseTest {

    @Mock RedisTemplate<String, Object>  redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @InjectMocks PresenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "ttlSeconds", 60L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── setStatus edge cases ──────────────────────────────────────────

    @Test
    @DisplayName("setStatus() — null userId is a no-op")
    void setStatus_nullUserId_noop() {
        sut.setStatus(null, "AWAY");
        verify(redisTemplate, never()).delete(anyString());
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("setStatus() — blank userId is a no-op")
    void setStatus_blankUserId_noop() {
        sut.setStatus("  ", "AWAY");
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("setStatus(AWAY) — falls back to configured TTL when Redis returns null TTL")
    void setStatus_away_nullTtl_fallsBackToDefault() {
        when(redisTemplate.getExpire("presence:user-1", TimeUnit.SECONDS)).thenReturn(null);

        sut.setStatus("user-1", "AWAY");

        verify(valueOps).set(eq("presence:user-1"), any(PresenceRecord.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("setStatus(AWAY) — falls back to configured TTL when Redis returns -1")
    void setStatus_away_negativeTtl_fallsBackToDefault() {
        when(redisTemplate.getExpire("presence:user-1", TimeUnit.SECONDS)).thenReturn(-1L);

        sut.setStatus("user-1", "AWAY");

        verify(valueOps).set(eq("presence:user-1"), any(PresenceRecord.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    // ── getPresence — LinkedHashMap deserialization ───────────────────

    @Test
    @DisplayName("getPresence() — deserializes LinkedHashMap with ISO-8601 lastSeenAt")
    void getPresence_linkedHashMap_isoString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ONLINE");
        map.put("lastSeenAt", Instant.now().toString()); // ISO-8601 string
        when(valueOps.get("presence:user-1")).thenReturn(map);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("ONLINE");
        assertThat(result.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("getPresence() — deserializes LinkedHashMap with epoch decimal lastSeenAt")
    void getPresence_linkedHashMap_epochDecimal() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "AWAY");
        map.put("lastSeenAt", "1714123456.123456789");
        when(valueOps.get("presence:user-1")).thenReturn(map);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("AWAY");
        assertThat(result.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("getPresence() — returns OFFLINE with null lastSeenAt for unparseable timestamp")
    void getPresence_linkedHashMap_unparseableTimestamp() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ONLINE");
        map.put("lastSeenAt", "NOT_A_DATE");
        when(valueOps.get("presence:user-1")).thenReturn(map);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("ONLINE");
        assertThat(result.getLastSeenAt()).isNull();
    }

    @Test
    @DisplayName("getPresence() — handles LinkedHashMap with null status gracefully")
    void getPresence_linkedHashMap_nullStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", null);
        map.put("lastSeenAt", null);
        when(valueOps.get("presence:user-1")).thenReturn(map);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("getPresence() — returns OFFLINE for blank userId")
    void getPresence_blankUserId_returnsOffline() {
        PresenceRecord result = sut.getPresence("  ");
        assertThat(result.getStatus()).isEqualTo("OFFLINE");
    }
}
