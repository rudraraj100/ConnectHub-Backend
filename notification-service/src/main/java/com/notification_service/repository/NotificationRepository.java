package com.notification_service.repository;

import com.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(String recipientId, boolean isRead);

    long countByRecipientIdAndIsRead(String recipientId, boolean isRead);

    List<Notification> findByType(String type);

    List<Notification> findByRoomId(String roomId);

    void deleteByNotificationId(Long notificationId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientId = :recipientId AND n.isRead = :isRead")
    void deleteByRecipientIdAndIsRead(@Param("recipientId") String recipientId,
                                      @Param("isRead") boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllReadByRecipientId(@Param("recipientId") String recipientId);
}
