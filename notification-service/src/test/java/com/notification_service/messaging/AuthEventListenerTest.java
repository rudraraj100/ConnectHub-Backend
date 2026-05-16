package com.notification_service.messaging;

import com.notification_service.service.NotifService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthEventListener - unit tests")
class AuthEventListenerTest {

    @Mock
    private NotifService notifService;

    @InjectMocks
    private AuthEventListener sut;

    @Test
    @DisplayName("handleAuthEvent() USER_VERIFIED sends welcome notification and email")
    void handleAuthEvent_userVerified_sendsNotifAndEmail() {
        AuthEventMessage event = new AuthEventMessage("user-1", "alice@example.com", "Alice Smith", "USER_VERIFIED");

        sut.handleAuthEvent(event);

        // 1. In-app notification
        verify(notifService).send(argThat(req -> 
            req.getRecipientId().equals("user-1") && 
            req.getTitle().contains("Welcome") &&
            req.getMessage().contains("Alice Smith")
        ));

        // 2. Welcome email
        verify(notifService).sendEmail(eq("alice@example.com"), contains("Welcome"), contains("verified"));
    }

    @Test
    @DisplayName("handleAuthEvent() PLAN_UPGRADED sends premium notification and email")
    void handleAuthEvent_planUpgraded_sendsNotifAndEmail() {
        AuthEventMessage event = new AuthEventMessage("user-1", "alice@example.com", "Alice Smith", "PLAN_UPGRADED");

        sut.handleAuthEvent(event);

        // 1. In-app notification
        verify(notifService).send(argThat(req -> 
            req.getRecipientId().equals("user-1") && 
            req.getTitle().contains("Premium")
        ));

        // 2. Congratulations email
        verify(notifService).sendEmail(eq("alice@example.com"), contains("Premium"), contains("upgraded"));
    }

    @Test
    @DisplayName("handleAuthEvent() ignores null or incomplete events")
    void handleAuthEvent_ignoresInvalid() {
        sut.handleAuthEvent(null);
        sut.handleAuthEvent(new AuthEventMessage()); // missing type/userId
        
        verifyNoInteractions(notifService);
    }

    @Test
    @DisplayName("handleAuthEvent() ignores unknown event types")
    void handleAuthEvent_ignoresUnknown() {
        AuthEventMessage event = new AuthEventMessage("u1", "e1", "n1", "UNKNOWN_TYPE");
        
        sut.handleAuthEvent(event);
        
        verifyNoInteractions(notifService);
    }
}
