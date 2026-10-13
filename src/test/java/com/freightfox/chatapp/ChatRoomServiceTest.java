package com.freightfox.chatapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.config.ChatProperties;
import com.freightfox.chatapp.dto.MessageDto;
import com.freightfox.chatapp.exception.DuplicateRoomNameException;
import com.freightfox.chatapp.exception.RoomNotFoundException;
import com.freightfox.chatapp.redis.RedisChatStore;
import com.freightfox.chatapp.service.ChatRoomService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private RedisChatStore chatStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatProperties properties = new ChatProperties("/ws/chat", 10, 100, 500);

    private ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService(chatStore, objectMapper, properties);
    }

    @Test
    void createRoomReturnsTrimmedRoomIdWhenCreated() {
        when(chatStore.createRoom(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString())).thenReturn(true);

        assertThat(service.createRoom(" general ")).isEqualTo("general");
    }

    @Test
    void createRoomThrowsOnDuplicate() {
        when(chatStore.createRoom(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.createRoom("general"))
                .isInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void joinRoomThrowsWhenRoomMissing() {
        when(chatStore.roomExists("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.joinRoom("ghost", "sandy"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void sendMessagePersistsAndPublishesSameJson() throws Exception {
        when(chatStore.roomExists("general")).thenReturn(true);

        MessageDto dto = service.sendMessage("general", "sandy", "hello");

        assertThat(dto.participant()).isEqualTo("sandy");
        assertThat(dto.message()).isEqualTo("hello");
        assertThat(dto.timestamp()).isNotBlank();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatStore).pushMessage(ArgumentMatchers.eq("general"), jsonCaptor.capture());
        verify(chatStore).publish(ArgumentMatchers.eq("general"), jsonCaptor.capture());

        MessageDto stored = objectMapper.readValue(jsonCaptor.getValue(), MessageDto.class);
        assertThat(stored.message()).isEqualTo("hello");
        assertThat(stored.participant()).isEqualTo("sandy");
    }

    @Test
    void sendMessageThrowsWhenRoomMissing() {
        when(chatStore.roomExists("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage("ghost", "sandy", "hi"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getMessagesReturnsEmptyWhenNoneStored() {
        when(chatStore.roomExists("general")).thenReturn(true);
        when(chatStore.lastMessages("general", 10)).thenReturn(List.of());

        assertThat(service.getMessages("general", null)).isEmpty();
    }

    @Test
    void getMessagesDeserializesStoredJson() throws Exception {
        when(chatStore.roomExists("general")).thenReturn(true);
        MessageDto original = new MessageDto("sandy", "hi", "2024-01-01T10:00:00Z");
        when(chatStore.lastMessages("general", 10))
                .thenReturn(List.of(objectMapper.writeValueAsString(original)));

        assertThat(service.getMessages("general", null)).containsExactly(original);
    }

    @Test
    void getMessagesClampsLimitToMaximum() {
        when(chatStore.roomExists("general")).thenReturn(true);
        when(chatStore.lastMessages("general", 100)).thenReturn(List.of());

        service.getMessages("general", 999);

        verify(chatStore).lastMessages("general", 100);
    }

    @Test
    void getMessagesRejectsNonPositiveLimit() {
        when(chatStore.roomExists("general")).thenReturn(true);

        assertThatThrownBy(() -> service.getMessages("general", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRoomDeletesAnExistingRoom() {
        when(chatStore.roomExists("general")).thenReturn(true);

        service.deleteRoom("general");

        verify(chatStore).deleteRoom("general");
    }

    @Test
    void deleteRoomThrowsWhenRoomMissing() {
        when(chatStore.roomExists("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRoom("ghost"))
                .isInstanceOf(RoomNotFoundException.class);
    }
}