package com.notification_service.resource;

import com.notification_service.dto.*;
import com.notification_service.service.NotifService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotifResource - unit tests")
class NotifResourceTest {

    @Mock
    private NotifService notifService;

    @InjectMocks
    private NotifResource sut;

    @Test
    @DisplayName("getByRecipient() calls service and returns 200 OK")
    void getByRecipient_callsService() {
        when(notifService.getByRecipient("user-1")).thenReturn(List.of(new NotificationDTO()));

        ResponseEntity<ApiResponse<List<NotificationDTO>>> resp = sut.getByRecipient("user-1");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getData()).hasSize(1);
        verify(notifService).getByRecipient("user-1");
    }

    @Test
    @DisplayName("getUnreadCount() calls service")
    void getUnreadCount_callsService() {
        when(notifService.getUnreadCount("user-1")).thenReturn(5L);

        ResponseEntity<ApiResponse<Long>> resp = sut.getUnreadCount("user-1");

        assertThat(resp.getBody().getData()).isEqualTo(5L);
        verify(notifService).getUnreadCount("user-1");
    }

    @Test
    @DisplayName("send() delegates to service")
    void send_delegates() {
        SendNotificationRequest req = new SendNotificationRequest();
        when(notifService.send(any())).thenReturn(new NotificationDTO());

        ResponseEntity<ApiResponse<NotificationDTO>> resp = sut.send(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notifService).send(req);
    }

    @Test
    @DisplayName("markAsRead() calls service")
    void markAsRead_callsService() {
        ResponseEntity<ApiResponse<Void>> resp = sut.markAsRead(123L);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notifService).markAsRead(123L);
    }

    @Test
    @DisplayName("broadcast() skips if no recipients")
    void broadcast_noRecipients_skips() {
        BroadcastRequest req = new BroadcastRequest();
        req.setRecipientIds(List.of());

        ResponseEntity<ApiResponse<Void>> resp = sut.broadcast(req);

        assertThat(resp.getBody().getMessage()).contains("No recipients");
        verifyNoInteractions(notifService);
    }

    @Test
    @DisplayName("broadcast() sends bulk if recipients present")
    void broadcast_sendsBulk() {
        BroadcastRequest req = new BroadcastRequest();
        req.setRecipientIds(List.of("u1", "u2"));
        req.setTitle("Hello");
        req.setMessage("World");

        ResponseEntity<ApiResponse<Void>> resp = sut.broadcast(req);

        assertThat(resp.getBody().getMessage()).contains("Broadcast sent");
        verify(notifService).sendBulk(List.of("u1", "u2"), "SYSTEM", "Hello", "World");
    }
}
