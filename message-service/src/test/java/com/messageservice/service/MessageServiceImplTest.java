package com.messageservice.service;

import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.entity.Message;
import com.messageservice.repository.MessageRepository;
import com.messageservice.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl — unit tests")
class MessageServiceImplTest {

    @Mock MessageRepository messageRepository;

    @InjectMocks MessageServiceImpl sut;

    private Message existingMessage;

    @BeforeEach
    void setUp() {
        existingMessage = Message.builder()
                .messageId("msg-1")
                .roomId("room-1")
                .senderId("user-1")
                .content("Hello world")
                .type("TEXT")
                .deliveryStatus("SENT")
                .isDeleted(false)
                .isEdited(false)
                .isPinned(false)
                .sentAt(LocalDateTime.now())
                .build();
    }

    // ── sendMessage ───────────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage() saves and returns a MessageResponse")
    void sendMessage_savesAndReturnsResponse() {
        when(messageRepository.save(any())).thenReturn(existingMessage);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hello world");
        req.setType("TEXT");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        verify(messageRepository).save(any(Message.class));
        assertThat(resp).isNotNull();
        assertThat(resp.getMessageId()).isEqualTo("msg-1");
        assertThat(resp.getContent()).isEqualTo("Hello world");
    }

    // ── editMessage ───────────────────────────────────────────────────

    @Test
    @DisplayName("editMessage() throws 404 when message not found")
    void editMessage_notFound_throws404() {
        when(messageRepository.findByMessageId("missing")).thenReturn(Optional.empty());

        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Updated");

        assertThatThrownBy(() -> sut.editMessage("missing", "user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("editMessage() throws 403 when requester is not the sender")
    void editMessage_notOwner_throws403() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(existingMessage));

        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Tampered");

        assertThatThrownBy(() -> sut.editMessage("msg-1", "other-user", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("own messages");
    }

    @Test
    @DisplayName("editMessage() updates content and sets isEdited=true")
    void editMessage_success() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(existingMessage));
        when(messageRepository.save(any())).thenReturn(existingMessage);

        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Updated text");

        sut.editMessage("msg-1", "user-1", req);

        assertThat(existingMessage.getContent()).isEqualTo("Updated text");
        assertThat(existingMessage.isEdited()).isTrue();
        verify(messageRepository).save(existingMessage);
    }

    // ── deleteMessage ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleteMessage() throws 404 when message not found")
    void deleteMessage_notFound_throws404() {
        when(messageRepository.findByMessageId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.deleteMessage("missing", "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("deleteMessage() throws 403 when requester is not the sender")
    void deleteMessage_notOwner_throws403() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(existingMessage));

        assertThatThrownBy(() -> sut.deleteMessage("msg-1", "other-user"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("own messages");
    }

    @Test
    @DisplayName("deleteMessage() soft-deletes the message")
    void deleteMessage_success_marksDeleted() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(existingMessage));

        sut.deleteMessage("msg-1", "user-1");

        assertThat(existingMessage.isDeleted()).isTrue();
        verify(messageRepository).save(existingMessage);
    }

    // ── pinMessage / unpinMessage ─────────────────────────────────────

    @Test
    @DisplayName("pinMessage() unpins all then pins the specified message")
    void pinMessage_success() {
        when(messageRepository.unpinAllInRoom("room-1")).thenReturn(0);
        when(messageRepository.pinMessage("msg-1")).thenReturn(1);

        sut.pinMessage("room-1", "msg-1");

        verify(messageRepository).unpinAllInRoom("room-1");
        verify(messageRepository).pinMessage("msg-1");
    }

    @Test
    @DisplayName("unpinMessage() unpins all messages in room")
    void unpinMessage_success() {
        when(messageRepository.unpinAllInRoom("room-1")).thenReturn(1);

        sut.unpinMessage("room-1");

        verify(messageRepository).unpinAllInRoom("room-1");
    }

    // ── markAllAsReadInRoom ───────────────────────────────────────────

    @Test
    @DisplayName("markAllAsReadInRoom() returns updated row count")
    void markAllAsReadInRoom_returnsCount() {
        when(messageRepository.markAllAsReadInRoom("room-1", "reader-1")).thenReturn(5);

        int result = sut.markAllAsReadInRoom("room-1", "reader-1");

        assertThat(result).isEqualTo(5);
    }

    // ── updateDeliveryStatus ──────────────────────────────────────────

    @Test
    @DisplayName("updateDeliveryStatus() saves updated status")
    void updateDeliveryStatus_success() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(existingMessage));

        sut.updateDeliveryStatus("msg-1", "READ");

        assertThat(existingMessage.getDeliveryStatus()).isEqualTo("READ");
        verify(messageRepository).save(existingMessage);
    }
}
