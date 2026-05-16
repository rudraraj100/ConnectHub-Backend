package com.notification_service.service;

import com.notification_service.dto.NotificationDTO;
import com.notification_service.dto.SendNotificationRequest;
import com.notification_service.entity.Notification;
import com.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotifServiceImpl — unit tests")
class NotifServiceImplTest {

    @Mock NotificationRepository repo;
    @Mock JavaMailSender          mailSender;

    @InjectMocks NotifServiceImpl sut;

    private Notification savedNotif;

    @BeforeEach
    void setUp() {
        savedNotif = Notification.builder()
                .notificationId(1L)
                .recipientId("user-1")
                .actorId("user-1")
                .type("SYSTEM")
                .title("Hello")
                .message("World")
                .isRead(false)
                .build();
    }

    // ── send ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() persists notification to DB")
    void send_persistsToDatabase() {
        when(repo.save(any())).thenReturn(savedNotif);

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId("user-1");
        req.setActorId("user-1");
        req.setType("SYSTEM");
        req.setTitle("Hello");
        req.setMessage("World");

        sut.send(req);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo("user-1");
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    @DisplayName("send() returns a mapped DTO")
    void send_returnsDto() {
        when(repo.save(any())).thenReturn(savedNotif);

        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId("user-1");
        req.setType("SYSTEM");
        req.setTitle("Hello");
        req.setMessage("World");

        NotificationDTO dto = sut.send(req);

        assertThat(dto).isNotNull();
        assertThat(dto.getNotificationId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Hello");
        assertThat(dto.isRead()).isFalse();
    }

    // ── markAsRead ────────────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead() sets isRead=true and saves")
    void markAsRead_setsReadFlag() {
        when(repo.findById(1L)).thenReturn(Optional.of(savedNotif));

        sut.markAsRead(1L);

        assertThat(savedNotif.isRead()).isTrue();
        verify(repo).save(savedNotif);
    }

    @Test
    @DisplayName("markAsRead() does nothing when notification not found")
    void markAsRead_notFound_noop() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        sut.markAsRead(99L);

        verify(repo, never()).save(any());
    }

    // ── markAllRead ───────────────────────────────────────────────────

    @Test
    @DisplayName("markAllRead() delegates to repository bulk update")
    void markAllRead_callsRepository() {
        sut.markAllRead("user-1");
        verify(repo).markAllReadByRecipientId("user-1");
    }

    // ── getByRecipient ────────────────────────────────────────────────

    @Test
    @DisplayName("getByRecipient() returns mapped DTO list")
    void getByRecipient_returnsList() {
        when(repo.findByRecipientIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(savedNotif));

        List<NotificationDTO> result = sut.getByRecipient("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientId()).isEqualTo("user-1");
    }

    // ── getUnreadCount ────────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount() returns correct count from repository")
    void getUnreadCount_returnsCount() {
        when(repo.countByRecipientIdAndIsRead("user-1", false)).thenReturn(5L);

        long count = sut.getUnreadCount("user-1");

        assertThat(count).isEqualTo(5L);
    }

    // ── deleteNotification ────────────────────────────────────────────

    @Test
    @DisplayName("deleteNotification() calls repository delete")
    void deleteNotification_callsRepository() {
        sut.deleteNotification(1L);
        verify(repo).deleteByNotificationId(1L);
    }

    // ── sendEmail ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail() calls JavaMailSender with correct fields")
    void sendEmail_callsMailSender() {
        sut.sendEmail("alice@example.com", "Subject", "Body text");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(1000)).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("alice@example.com");
        assertThat(msg.getSubject()).isEqualTo("Subject");
        assertThat(msg.getText()).isEqualTo("Body text");
    }

    @Test
    @DisplayName("sendEmail() swallows exceptions — does not propagate")
    void sendEmail_mailSenderThrows_doesNotPropagate() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Must not throw
        assertThatNoException().isThrownBy(
                () -> sut.sendEmail("alice@example.com", "Subj", "Body"));
    }
}
