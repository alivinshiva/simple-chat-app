package com.freightfox.chatapp.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.service.RealtimeSessionRegistry;
import java.io.IOException;
import java.util.Map;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Listens on the pattern {@code chat.room.*} and relays each published message to the
 * live {@link WebSocketSession}s registered for that room via
 * {@link RealtimeSessionRegistry}. A socket that can no longer be written to is
 * evicted (its durable membership in the Redis Set is untouched).
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    private final StringRedisSerializer serializer = new StringRedisSerializer();
    private final RealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(RealtimeSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = serializer.deserialize(message.getChannel());
        String body = serializer.deserialize(message.getBody());
        if (channel == null || body == null || !channel.startsWith(RedisChatStore.CHANNEL_PREFIX)) {
            return;
        }
        String roomId = channel.substring(RedisChatStore.CHANNEL_PREFIX.length());
        TextMessage outbound = buildOutbound(body);
        for (WebSocketSession session : sessionRegistry.sessions(roomId)) {
            if (!session.isOpen()) {
                sessionRegistry.leave(session);
            } else {
                try {
                    session.sendMessage(outbound);
                } catch (IOException e) {
                    sessionRegistry.leave(session);
                }
            }
        }
    }

    private TextMessage buildOutbound(String body) {
        try {
            Map<String, Object> envelope = Map.of(
                    "type", "message",
                    "payload", objectMapper.readTree(body));
            return new TextMessage(objectMapper.writeValueAsString(envelope));
        } catch (IOException e) {
            return new TextMessage("{\"type\":\"message\",\"payload\":" + body + "}");
        }
    }
}