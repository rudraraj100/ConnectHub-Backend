package com.auth_service.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthEventMessage - unit tests")
class AuthEventMessageTest {

    @Test
    @DisplayName("DTO supports constructors and getters")
    void dto_works() {
        AuthEventMessage msg = new AuthEventMessage("u1", "e1", "n1", "t1");
        assertThat(msg.getUserId()).isEqualTo("u1");
        assertThat(msg.getEventType()).isEqualTo("t1");
    }
}
