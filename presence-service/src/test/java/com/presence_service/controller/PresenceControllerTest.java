package com.presence_service.controller;

import com.presence_service.dto.ApiResponse;
import com.presence_service.dto.PresenceRecord;
import com.presence_service.service.PresenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceController - unit tests")
class PresenceControllerTest {

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private PresenceController sut;

    @Test
    @DisplayName("heartbeat() calls service with userId header")
    void heartbeat_success() {
        ResponseEntity<ApiResponse<Void>> resp = sut.heartbeat("u1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(presenceService).heartbeat("u1");
    }

    @Test
    @DisplayName("setStatus() validates input and calls service")
    void setStatus_validInput() {
        ResponseEntity<ApiResponse<Void>> resp = sut.setStatus("u1", "AWAY");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(presenceService).setStatus("u1", "AWAY");
    }

    @Test
    @DisplayName("setStatus() returns 400 for invalid status")
    void setStatus_invalidInput() {
        ResponseEntity<ApiResponse<Void>> resp = sut.setStatus("u1", "INVALID");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getPresence() returns record")
    void getPresence_success() {
        PresenceRecord record = new PresenceRecord();
        record.setUserId("u1");
        record.setStatus("ONLINE");
        when(presenceService.getPresence("u1")).thenReturn(record);

        ResponseEntity<ApiResponse<PresenceRecord>> resp = sut.getPresence("u1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getStatus()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("getBulkPresence() returns map")
    void getBulkPresence_success() {
        List<String> ids = List.of("u1", "u2");
        when(presenceService.getBulkPresence(ids)).thenReturn(Map.of());

        ResponseEntity<ApiResponse<Map<String, PresenceRecord>>> resp = sut.getBulkPresence(ids);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(presenceService).getBulkPresence(ids);
    }

    @Test
    @DisplayName("getBulkPresence() returns 400 for empty list")
    void getBulkPresence_emptyList() {
        ResponseEntity<ApiResponse<Map<String, PresenceRecord>>> resp = sut.getBulkPresence(List.of());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
