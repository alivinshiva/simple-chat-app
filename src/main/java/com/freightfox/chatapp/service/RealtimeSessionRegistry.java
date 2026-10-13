package com.freightfox.chatapp.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * In-memory registry mapping live WebSocket sessions to the room they joined and
 * back. Holds <em>only live sockets</em>: durable room membership lives in the Redis
 * Set, while this class exists purely to let the pub/sub subscriber deliver frames to
 * sockets on this process.
 */
@Component
public class RealtimeSessionRegistry {

    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public void join(WebSocketSession session, String roomId) {
        String sessionId = session.getId();
        String previous = sessionRoom.put(sessionId, roomId);
        if (previous != null) {
            Set<WebSocketSession> previousRoom = roomSessions.get(previous);
            if (previousRoom != null) {
                previousRoom.remove(session);
            }
        }
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void leave(WebSocketSession session) {
        String sessionId = session.getId();
        String roomId = sessionRoom.remove(sessionId);
        if (roomId != null) {
            Set<WebSocketSession> room = roomSessions.get(roomId);
            if (room != null) {
                room.remove(session);
            }
        }
    }

    public String roomOf(WebSocketSession session) {
        return sessionRoom.get(session.getId());
    }

    public Set<WebSocketSession> sessions(String roomId) {
        Set<WebSocketSession> room = roomSessions.get(roomId);
        return room == null || room.isEmpty() ? Set.of() : Set.copyOf(room);
    }
}