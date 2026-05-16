package com.presence_service.service;

import com.presence_service.dto.PresenceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceServiceImpl — unit tests")
class PresenceServiceImplTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @InjectMocks PresenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        // inject @Value fields
        ReflectionTestUtils.setField(sut, "ttlSeconds", 60L);
        // lenient: some tests (null-userId guards, OFFLINE branch) don't call opsForValue()
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── heartbeat ─────────────────────────────────────────────────────

    @Test
    @DisplayName("heartbeat() stores ONLINE record in Redis with TTL")
    void heartbeat_storesOnlineRecord() {
        sut.heartbeat("user-1");

        ArgumentCaptor<PresenceRecord> captor = ArgumentCaptor.forClass(PresenceRecord.class);
        verify(valueOps).set(eq("presence:user-1"), captor.capture(), eq(60L), eq(TimeUnit.SECONDS));
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("heartbeat() with null userId is a no-op")
    void heartbeat_nullUserId_noop() {
        sut.heartbeat(null);
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    // ── setStatus ─────────────────────────────────────────────────────

    @Test
    @DisplayName("setStatus(OFFLINE) deletes the Redis key")
    void setStatus_offline_deletesKey() {
        sut.setStatus("user-1", "OFFLINE");
        verify(redisTemplate).delete("presence:user-1");
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("setStatus(AWAY) stores AWAY record preserving TTL")
    void setStatus_away_storesRecord() {
        when(redisTemplate.getExpire("presence:user-1", TimeUnit.SECONDS)).thenReturn(30L);

        sut.setStatus("user-1", "AWAY");

        ArgumentCaptor<PresenceRecord> captor = ArgumentCaptor.forClass(PresenceRecord.class);
        verify(valueOps).set(eq("presence:user-1"), captor.capture(), eq(30L), eq(TimeUnit.SECONDS));
        assertThat(captor.getValue().getStatus()).isEqualTo("AWAY");
    }

    // ── getPresence ───────────────────────────────────────────────────

    @Test
    @DisplayName("getPresence() returns ONLINE record from Redis")
    void getPresence_returnsOnlineRecord() {
        PresenceRecord stored = PresenceRecord.builder()
                .userId("user-1").status("ONLINE").lastSeenAt(Instant.now()).build();
        when(valueOps.get("presence:user-1")).thenReturn(stored);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("getPresence() returns OFFLINE when key absent in Redis")
    void getPresence_keyAbsent_returnsOffline() {
        when(valueOps.get("presence:user-1")).thenReturn(null);

        PresenceRecord result = sut.getPresence("user-1");

        assertThat(result.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("getPresence() returns OFFLINE for null userId")
    void getPresence_nullUserId_returnsOffline() {
        PresenceRecord result = sut.getPresence(null);
        assertThat(result.getStatus()).isEqualTo("OFFLINE");
    }

    // ── getBulkPresence ───────────────────────────────────────────────

    @Test
    @DisplayName("getBulkPresence() aggregates per-user presence into a map")
    void getBulkPresence_returnsMap() {
        PresenceRecord r1 = PresenceRecord.builder().userId("u1").status("ONLINE").build();
        PresenceRecord r2 = PresenceRecord.builder().userId("u2").status("OFFLINE").build();
        when(valueOps.get("presence:u1")).thenReturn(r1);
        when(valueOps.get("presence:u2")).thenReturn(null);

        Map<String, PresenceRecord> result = sut.getBulkPresence(List.of("u1", "u2"));

        assertThat(result).hasSize(2);
        assertThat(result.get("u1").getStatus()).isEqualTo("ONLINE");
        assertThat(result.get("u2").getStatus()).isEqualTo("OFFLINE");
    }
}
