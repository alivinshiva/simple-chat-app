package com.freightfox.chatapp.redis;

import com.freightfox.chatapp.config.ChatProperties;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Facade over Redis for all chat persistence — the only class that knows the key
 * layout, so every other layer stays storage-agnostic.
 *
 * <p>Redis structures (per brief):</p>
 * <ul>
 *   <li><b>Hash</b> {@code chat:room:{roomId}} — room metadata (name, createdAt); a
 *       successful {@code HSETNX} is what guarantees room-name uniqueness.</li>
 *   <li><b>Set</b> {@code chat:room:{roomId}:participants} — durable member roster.</li>
 *   <li><b>List</b> {@code chat:room:{roomId}:messages} — message history as JSON
 *       strings in chronological order, capped to {@code messageLogMax}.</li>
 *   <li><b>Pub/Sub</b> channel {@code chat.room.{roomId}} — real-time broadcast bus.</li>
 * </ul>
 */
@Component
public class RedisChatStore {

    static final String ROOM_KEY_PREFIX = "chat:room:";
    static final String CHANNEL_PREFIX = "chat.room.";
    static final String PARTICIPANTS_SUFFIX = ":participants";
    static final String MESSAGES_SUFFIX = ":messages";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String NAME_FIELD = "name";

    private final RedisTemplate<String, String> redisTemplate;
    private final int messageLogMax;

    public RedisChatStore(RedisTemplate<String, String> redisTemplate, ChatProperties properties) {
        this.redisTemplate = redisTemplate;
        this.messageLogMax = properties.messageLogMax();
    }

    public static String roomKey(String roomId) {
        return ROOM_KEY_PREFIX + roomId;
    }

    public static String participantsKey(String roomId) {
        return roomKey(roomId) + PARTICIPANTS_SUFFIX;
    }

    public static String messagesKey(String roomId) {
        return roomKey(roomId) + MESSAGES_SUFFIX;
    }

    public static String channel(String roomId) {
        return CHANNEL_PREFIX + roomId;
    }

    public boolean createRoom(String roomId, String roomName, String createdAt) {
        var hash = redisTemplate.opsForHash();
        Boolean created = hash.putIfAbsent(roomKey(roomId), CREATED_AT_FIELD, createdAt);
        if (Boolean.TRUE.equals(created)) {
            hash.put(roomKey(roomId), NAME_FIELD, roomName);
            return true;
        }
        return false;
    }

    public boolean roomExists(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(roomKey(roomId)));
    }

    public long addParticipant(String roomId, String participant) {
        Long size = redisTemplate.opsForSet().add(participantsKey(roomId), participant);
        return size == null ? 0 : size;
    }

    public Set<String> participants(String roomId) {
        return redisTemplate.opsForSet().members(participantsKey(roomId));
    }

    public long pushMessage(String roomId, String json) {
        var list = redisTemplate.opsForList();
        Long size = list.rightPush(messagesKey(roomId), json);
        list.trim(messagesKey(roomId), -messageLogMax, -1);
        return size == null ? 0 : size;
    }

    public List<String> lastMessages(String roomId, long limit) {
        return redisTemplate.opsForList().range(messagesKey(roomId), -limit, -1);
    }

    public void deleteRoom(String roomId) {
        redisTemplate.delete(List.of(
                roomKey(roomId),
                participantsKey(roomId),
                messagesKey(roomId)));
    }

    public void publish(String roomId, String json) {
        redisTemplate.convertAndSend(channel(roomId), json);
    }
}