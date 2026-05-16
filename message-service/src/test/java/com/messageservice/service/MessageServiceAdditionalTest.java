package com.messageservice.service;

import com.messageservice.client.AuthServiceClient;
import com.messageservice.dto.request.EditMessageRequest;
import com.messageservice.dto.request.SendMessageRequest;
import com.messageservice.dto.response.MessageResponse;
import com.messageservice.entity.Message;
import com.messageservice.repository.MessageRepository;
import com.messageservice.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl — additional unit tests")
class MessageServiceAdditionalTest {

    @Mock MessageRepository  messageRepository;
    @Mock AuthServiceClient  authServiceClient;

    @InjectMocks MessageServiceImpl sut;

    private Message msg;

    @BeforeEach
    void setUp() {
        msg = Message.builder()
                .messageId("msg-1").roomId("room-1").senderId("user-1")
                .content("Hello").type("TEXT").deliveryStatus("SENT")
                .senderName("Alice")
                .isDeleted(false).isEdited(false).isPinned(false)
                .sentAt(LocalDateTime.now()).build();
    }

    // ── getMessageById ────────────────────────────────────────────────

    @Test
    @DisplayName("getMessageById() — returns response when found")
    void getMessageById_found() {
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(msg));
        Optional<MessageResponse> result = sut.getMessageById("msg-1");
        assertThat(result).isPresent();
        assertThat(result.get().getMessageId()).isEqualTo("msg-1");
    }

    @Test
    @DisplayName("getMessageById() — returns empty when not found")
    void getMessageById_notFound() {
        when(messageRepository.findByMessageId("bad")).thenReturn(Optional.empty());
        assertThat(sut.getMessageById("bad")).isEmpty();
    }

    // ── getMessagesByRoom ─────────────────────────────────────────────

    @Test
    @DisplayName("getMessagesByRoom() FREE — uses 30-day cutoff")
    void getMessagesByRoom_free_usesCutoff() {
        Page<Message> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByRoomIdAndSentAtAfterOrderBySentAtDesc(
                eq("room-1"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

        Page<MessageResponse> result = sut.getMessagesByRoom("room-1", 0, 20, "FREE");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMessagesByRoom() PREMIUM — uses unlimited history")
    void getMessagesByRoom_premium_noDateCutoff() {
        Page<Message> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByRoomIdOrderBySentAtDesc(eq("room-1"), any(Pageable.class)))
                .thenReturn(page);

        Page<MessageResponse> result = sut.getMessagesByRoom("room-1", 0, 20, "PREMIUM");

        assertThat(result.getContent()).hasSize(1);
        verify(messageRepository, never()).findByRoomIdAndSentAtAfterOrderBySentAtDesc(any(), any(), any());
    }

    @Test
    @DisplayName("getMessagesByRoom() default (no plan) — delegates to FREE path")
    void getMessagesByRoom_defaultNoPlan() {
        Page<Message> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByRoomIdAndSentAtAfterOrderBySentAtDesc(
                eq("room-1"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

        Page<MessageResponse> result = sut.getMessagesByRoom("room-1", 0, 20);

        assertThat(result).isNotNull();
    }

    // ── searchMessages ────────────────────────────────────────────────

    @Test
    @DisplayName("searchMessages() — returns matching messages")
    void searchMessages_returnsMatches() {
        when(messageRepository.searchInRoom("room-1", "hello")).thenReturn(List.of(msg));
        List<MessageResponse> result = sut.searchMessages("room-1", "hello");
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("searchMessages() — returns empty list when no matches")
    void searchMessages_empty() {
        when(messageRepository.searchInRoom("room-1", "xyz")).thenReturn(Collections.emptyList());
        assertThat(sut.searchMessages("room-1", "xyz")).isEmpty();
    }

    // ── getMessageCount ───────────────────────────────────────────────

    @Test
    @DisplayName("getMessageCount() — returns count from repository")
    void getMessageCount_returnsCount() {
        when(messageRepository.countByRoomId("room-1")).thenReturn(42L);
        assertThat(sut.getMessageCount("room-1")).isEqualTo(42L);
    }

    // ── getMessagesBefore ─────────────────────────────────────────────

    @Test
    @DisplayName("getMessagesBefore() — returns messages after given timestamp")
    void getMessagesBefore_returnsList() {
        when(messageRepository.findByRoomIdAndSentAtAfter(eq("room-1"), any()))
                .thenReturn(List.of(msg));
        List<MessageResponse> result = sut.getMessagesBefore("room-1", LocalDateTime.now().minusDays(1));
        assertThat(result).hasSize(1);
    }

    // ── sendMessage with senderName ───────────────────────────────────

    @Test
    @DisplayName("sendMessage() — persists senderName so auth-service call is skipped")
    void sendMessage_withSenderName_skipsAuthService() {
        when(messageRepository.save(any())).thenReturn(msg);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hi");
        req.setType("TEXT");
        req.setSenderName("Alice");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getSenderName()).isEqualTo("Alice");
        verify(authServiceClient, never()).getUserById(any());
    }

    // ── toResponse — auth-service enrichment ─────────────────────────

    @Test
    @DisplayName("sendMessage() — falls back to auth-service when senderName is null")
    void sendMessage_noSenderName_callsAuthService() {
        msg.setSenderName(null);
        when(messageRepository.save(any())).thenReturn(msg);

        Map<String, Object> dataMap = Map.of("fullName", "Alice Smith", "username", "alice", "avatarUrl", "http://img");
        Map<String, Object> authResp = Map.of("data", dataMap);
        when(authServiceClient.getUserById("user-1")).thenReturn(authResp);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hi");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getSenderName()).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("sendMessage() — falls back to UUID when auth-service throws")
    void sendMessage_authServiceThrows_fallsBackToUuid() {
        msg.setSenderName(null);
        when(messageRepository.save(any())).thenReturn(msg);
        when(authServiceClient.getUserById("user-1")).thenThrow(new RuntimeException("auth down"));

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hi");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getSenderName()).isEqualTo("user-1"); // UUID fallback
    }

    // ── editMessage — deleted message ─────────────────────────────────

    @Test
    @DisplayName("editMessage() — throws 400 when trying to edit deleted message")
    void editMessage_deleted_throws400() {
        msg.setDeleted(true);
        when(messageRepository.findByMessageId("msg-1")).thenReturn(Optional.of(msg));

        EditMessageRequest req = new EditMessageRequest();
        req.setContent("Updated");

        assertThatThrownBy(() -> sut.editMessage("msg-1", "user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("deleted");
    }

    // ── updateDeliveryStatus — not found ──────────────────────────────

    @Test
    @DisplayName("updateDeliveryStatus() — throws 404 when message not found")
    void updateDeliveryStatus_notFound_throws404() {
        when(messageRepository.findByMessageId("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.updateDeliveryStatus("bad", "READ"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── deriveMediaType ───────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage() — VIDEO type sets mediaType=VIDEO")
    void sendMessage_videoType_setsMediaType() {
        msg.setType("VIDEO");
        when(messageRepository.save(any())).thenReturn(msg);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("video");
        req.setType("VIDEO");
        req.setSenderName("Alice");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getMediaType()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("sendMessage() — FILE type sets mediaType=FILE")
    void sendMessage_fileType_setsMediaType() {
        msg.setType("FILE");
        when(messageRepository.save(any())).thenReturn(msg);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("file");
        req.setType("FILE");
        req.setSenderName("Alice");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getMediaType()).isEqualTo("FILE");
    }

    @Test
    @DisplayName("sendMessage() — TEXT type gives null mediaType")
    void sendMessage_textType_nullMediaType() {
        when(messageRepository.save(any())).thenReturn(msg);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("hi");
        req.setType("TEXT");
        req.setSenderName("Alice");

        MessageResponse resp = sut.sendMessage("room-1", "user-1", req);

        assertThat(resp.getMediaType()).isNull();
    }

    // ── getUnreadMessages ─────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadMessages() — returns messages after since")
    void getUnreadMessages_returnsList() {
        when(messageRepository.findByRoomIdAndSentAtAfter(eq("room-1"), any()))
                .thenReturn(List.of(msg));

        List<MessageResponse> result = sut.getUnreadMessages("room-1", LocalDateTime.now().minusHours(1));

        assertThat(result).hasSize(1);
    }
}
