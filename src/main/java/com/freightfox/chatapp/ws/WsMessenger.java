package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Writes typed JSON envelopes ({@code OutboundEnvelope}) back to a WebSocket session,
 * e.g. {@code {"type":"joined","payload":{...}}}. Silent on write failure; the caller
 * (or the subscriber) is responsible for evicting dead sessions.
 */
@Component
public class WsMessenger {

    private final ObjectMapper objectMapper;

    public WsMessenger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void send(WebSocketSession session, String type, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new OutboundEnvelope(type, payload));
            session.sendMessage(new TextMessage(json));
        } catch (IOException ignored) {
        }
    }

    public void sendError(WebSocketSession session, String code, String message) {
        send(session, "error", new ErrorPayload(code, message));
    }

    private record OutboundEnvelope(String type, Object payload) {
    }

    private record ErrorPayload(String code, String message) {
    }
}