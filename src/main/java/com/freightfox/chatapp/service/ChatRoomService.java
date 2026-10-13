package com.freightfox.chatapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.config.ChatProperties;
import com.freightfox.chatapp.dto.MessageDto;
import com.freightfox.chatapp.exception.DuplicateRoomNameException;
import com.freightfox.chatapp.exception.RoomNotFoundException;
import com.freightfox.chatapp.redis.RedisChatStore;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Application service owning all chat use cases. REST controller and WebSocket
 * handlers both call through here, so a message always takes the same unified write
 * path: {@code ensureRoomExists → serialize → append to list → publish to channel}.
 */
@Service
public class ChatRoomService {

    private final RedisChatStore chatStore;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;

    public ChatRoomService(RedisChatStore chatStore, ObjectMapper objectMapper, ChatProperties properties) {
        this.chatStore = chatStore;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String createRoom(String roomName) {
        String roomId = roomName.trim();
        String createdAt = Instant.now().toString();
        boolean created = chatStore.createRoom(roomId, roomId, createdAt);
        if (!created) {
            throw new DuplicateRoomNameException(roomId);
        }
        return roomId;
    }

    public void joinRoom(String roomId, String participant) {
        ensureRoomExists(roomId);
        chatStore.addParticipant(roomId, participant);
    }

    public MessageDto sendMessage(String roomId, String participant, String message) {
        ensureRoomExists(roomId);
        MessageDto dto = new MessageDto(participant, message, Instant.now().toString());
        try {
            String json = objectMapper.writeValueAsString(dto);
            chatStore.pushMessage(roomId, json);
            chatStore.publish(roomId, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message", e);
        }
        return dto;
    }

    public List<MessageDto> getMessages(String roomId, Integer limit) {
        ensureRoomExists(roomId);
        int safeLimit = resolveLimit(limit);
        List<MessageDto> messages = new ArrayList<>();
        for (String json : chatStore.lastMessages(roomId, safeLimit)) {
            try {
                messages.add(objectMapper.readValue(json, MessageDto.class));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize stored message", e);
            }
        }
        return messages;
    }

    public boolean roomExists(String roomId) {
        return chatStore.roomExists(roomId);
    }

    public void deleteRoom(String roomId) {
        ensureRoomExists(roomId);
        chatStore.deleteRoom(roomId);
    }

    private void ensureRoomExists(String roomId) {
        if (!chatStore.roomExists(roomId)) {
            throw new RoomNotFoundException(roomId);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return properties.historyDefaultLimit();
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be a positive integer");
        }
        return Math.min(limit, properties.historyMaxLimit());
    }
}