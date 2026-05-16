package com.notification_service.messaging;

import com.notification_service.dto.SendNotificationRequest;
import com.notification_service.service.NotifService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes auth-domain events from RabbitMQ queue: notification.auth.queue
 *
 * Routing key auth.user.verified → Welcome notification
 * Routing key auth.plan.upgraded → Premium upgrade notification
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private final NotifService notifService;

    @RabbitListener(queues = "notification.auth.queue")
    public void handleAuthEvent(AuthEventMessage event) {
        if (event == null || event.getEventType() == null || event.getUserId() == null) {
            log.warn("[RabbitMQ] Received null or incomplete auth event — skipping");
            return;
        }

        log.info("[RabbitMQ] Auth event received: type={} userId={}",
                event.getEventType(), event.getUserId());

        switch (event.getEventType()) {

            case "USER_VERIFIED" -> {
                String displayName = event.getFullName() != null ? event.getFullName() : "there";

                // 1️⃣  In-app notification (DB-persisted, shown in notification bell)
                SendNotificationRequest req = new SendNotificationRequest();
                req.setRecipientId(event.getUserId());
                req.setActorId(event.getUserId());
                req.setType("SYSTEM");
                req.setTitle("Welcome to ConnectHub! 🎉");
                req.setMessage("Hi " + displayName + "! Your email is verified. Start chatting now.");
                notifService.send(req);

                // 2️⃣  Welcome email (async — does not block the RabbitMQ listener thread)
                if (event.getEmail() != null && !event.getEmail().isBlank()) {
                    String subject = "Welcome to ConnectHub! 🎉";
                    String body = String.join("\n",
                            "Hi " + displayName + ",",
                            "",
                            "Your email has been verified and your ConnectHub account is now active!",
                            "",
                            "Here's what you can do next:",
                            "  • Join or create chat rooms",
                            "  • Connect with friends and colleagues",
                            "  • Upgrade to Premium for exclusive features",
                            "",
                            "Jump in and start chatting: http://localhost:4200",
                            "",
                            "Cheers,",
                            "The ConnectHub Team"
                    );
                    notifService.sendEmail(event.getEmail(), subject, body);
                    log.info("[Auth Event] Welcome email sent to {}", event.getEmail());
                }

                log.info("[Auth Event] USER_VERIFIED handled for userId={}", event.getUserId());
            }

            case "PLAN_UPGRADED" -> {
                String displayName = event.getFullName() != null ? event.getFullName() : "there";

                // 1️⃣  In-app notification
                SendNotificationRequest req = new SendNotificationRequest();
                req.setRecipientId(event.getUserId());
                req.setActorId(event.getUserId());
                req.setType("SYSTEM");
                req.setTitle("You're now a Premium member! ✨");
                req.setMessage("Enjoy unlimited rooms, pinned messages, and all exclusive ConnectHub features.");
                notifService.send(req);

                // 2️⃣  Congratulations email (async)
                if (event.getEmail() != null && !event.getEmail().isBlank()) {
                    String subject = "You're now a ConnectHub Premium member! ✨";
                    String body = String.join("\n",
                            "Hi " + displayName + ",",
                            "",
                            "Congratulations! 🎉 Your ConnectHub account has been upgraded to Premium.",
                            "",
                            "Your exclusive Premium benefits are now active:",
                            "  ★ Unlimited chat rooms",
                            "  ★ Pin important messages",
                            "  ★ Priority support",
                            "  ★ Exclusive Premium badge",
                            "",
                            "Start enjoying your new features: http://localhost:4200",
                            "",
                            "Thank you for supporting ConnectHub!",
                            "",
                            "Cheers,",
                            "The ConnectHub Team"
                    );
                    notifService.sendEmail(event.getEmail(), subject, body);
                    log.info("[Auth Event] Premium congratulations email sent to {}", event.getEmail());
                }

                log.info("[Auth Event] PLAN_UPGRADED handled for userId={}", event.getUserId());
            }

            default -> log.warn("[RabbitMQ] Unknown event type: {}", event.getEventType());
        }
    }
}
