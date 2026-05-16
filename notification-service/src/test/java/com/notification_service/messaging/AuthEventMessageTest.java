package com.notification_service.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthEventMessage - unit tests")
class AuthEventMessageTest {

    @Test
    @DisplayName("DTO supports no-args constructor and setters")
    void dto_settersAndGetters() {
        AuthEventMessage msg = new AuthEventMessage();
        msg.setUserId("u1");
        msg.setEmail("e1");
        msg.setFullName("n1");
        msg.setEventType("t1");

        assertThat(msg.getUserId()).isEqualTo("u1");
        assertThat(msg.getEmail()).isEqualTo("e1");
        assertThat(msg.getFullName()).isEqualTo("n1");
        assertThat(msg.getEventType()).isEqualTo("t1");
    }

    @Test
    @DisplayName("DTO supports all-args constructor")
    void dto_allArgs() {
        AuthEventMessage msg = new AuthEventMessage("u1", "e1", "n1", "t1");
        assertThat(msg.getUserId()).isEqualTo("u1");
        assertThat(msg.getEventType()).isEqualTo("t1");
    }
}
