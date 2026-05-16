package com.notification_service.service;

import com.notification_service.dto.NotificationDTO;
import com.notification_service.dto.SendNotificationRequest;
import com.notification_service.entity.Notification;
import com.notification_service.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NotifServiceImpl handles the delivery of notifications.
 * It persists notifications to the database and sends asynchronous emails for important events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifServiceImpl implements NotifService {

    private final NotificationRepository repo;
    private final JavaMailSender         mailSender;

    /**
     * Saves a new notification to the database. 
     * These are fetched by the frontend to show the notification list.
     */
    @Override
    @Transactional
    public NotificationDTO send(SendNotificationRequest req) {
        Notification n = Notification.builder()
                .recipientId(req.getRecipientId())
                .actorId(req.getActorId())
                .type(req.getType())
                .title(req.getTitle())
                .message(req.getMessage())
                .roomId(req.getRoomId())
                .messageId(req.getMessageId())
                .isRead(false)
                .build();

        n = repo.save(n);

        NotificationDTO dto = toDto(n);

        log.debug("Notification saved for {} — type={}", req.getRecipientId(), req.getType());

        return dto;
    }

    // ── sendBulk ─────────────────────────────────────────────────────────────

    @Override
    @Async   // fire-and-forget: controller returns 200 immediately; DB writes happen in background
    public void sendBulk(List<String> recipientIds, String type, String title, String message) {
        for (String id : recipientIds) {
            try {
                SendNotificationRequest req = new SendNotificationRequest();
                req.setRecipientId(id);
                req.setType(type);
                req.setTitle(title);
                req.setMessage(message);
                send(req);
            } catch (Exception e) {
                log.error("[Broadcast] Failed to notify user {}: {}", id, e.getMessage());
            }
        }
        log.info("[Broadcast] Delivered {} notifications — type={} title='{}'",
                recipientIds.size(), type, title);
    }

    // ── sendPushNotification (FCM) ────────────────────────────────────────────

    @Override
    @Async
    public void sendPushNotification(String recipientId, String title, String body) {
        // FCM integration — requires firebase-admin and service account JSON
        // Enable in application.yaml: app.firebase.enabled=true
        log.info("[FCM] Push notification to {} — {}: {}", recipientId, title, body);
        // TODO: inject FirebaseMessaging bean when FCM is configured
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        repo.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            repo.save(n);
        });
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void markAllRead(String recipientId) {
        repo.markAllReadByRecipientId(recipientId);
    }

    // ── getByRecipient ────────────────────────────────────────────────────────

    @Override
    public List<NotificationDTO> getByRecipient(String recipientId) {
        return repo.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Override
    public List<NotificationDTO> getAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Override
    public long getUnreadCount(String recipientId) {
        return repo.countByRecipientIdAndIsRead(recipientId, false);
    }

    // ── deleteNotification ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        repo.deleteByNotificationId(notificationId);
    }

    // ── sendEmail ─────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("[Email] Sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("[Email] Failed to send to {}: {}", to, e.getMessage());
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private NotificationDTO toDto(Notification n) {
        return NotificationDTO.builder()
                .notificationId(n.getNotificationId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .roomId(n.getRoomId())
                .messageId(n.getMessageId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
