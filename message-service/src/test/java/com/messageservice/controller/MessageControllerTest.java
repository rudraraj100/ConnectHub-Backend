package com.messageservice.controller;

import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.ApiResponse;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController - unit tests")
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private MessageController sut;

    @Test
    @DisplayName("send() delegates to service and returns 201 Created")
    void send_success() {
        when(request.getHeader("X-User-Id")).thenReturn("u1");
        SendMessageRequest body = new SendMessageRequest();
        when(messageService.sendMessage(eq("r1"), eq("u1"), any())).thenReturn(new MessageResponse());

        ResponseEntity<ApiResponse<MessageResponse>> resp = sut.send("r1", request, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(messageService).sendMessage(eq("r1"), eq("u1"), any());
    }

    @Test
    @DisplayName("getById() returns 200 OK when found")
    void getById_found() {
        MessageResponse msg = new MessageResponse();
        msg.setMessageId("m1");
        when(messageService.getMessageById("m1")).thenReturn(Optional.of(msg));

        ResponseEntity<ApiResponse<MessageResponse>> resp = sut.getById("m1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getMessageId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("getById() throws 404 when not found")
    void getById_notFound() {
        when(messageService.getMessageById("m1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getById("m1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Message not found");
    }

    @Test
    @DisplayName("markAllRead() calls service and returns OK")
    void markAllRead_success() {
        when(messageService.markAllAsReadInRoom("r1", "u1")).thenReturn(5);

        ResponseEntity<ApiResponse<Void>> resp = sut.markAllRead("r1", "u1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getMessage()).contains("5 messages");
        verify(messageService).markAllAsReadInRoom("r1", "u1");
    }

    @Test
    @DisplayName("userId() helper throws 401 if header missing")
    void userId_missing_throws401() {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        SendMessageRequest body = new SendMessageRequest();

        assertThatThrownBy(() -> sut.send("r1", request, body))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("X-User-Id header is required");
    }
}
