package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.service.RealtimeSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket entry point for {@code /ws/chat}. Parses the {@code {type, payload}}
 * envelope, rejects malformed frames with {@code INVALID_JSON}, and delegates typed
 * commands (join/chat) to the {@link ChatCommandDispatcher}. Closed or failed
 * connections are unregistered so no stalled socket keeps receiving frames.
 */
@Component
public class RealtimeChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatCommandDispatcher dispatcher;
    private final WsMessenger messenger;
    private final RealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeChatWebSocketHandler(ChatCommandDispatcher dispatcher, WsMessenger messenger,
                                        RealtimeSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.messenger = messenger;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(textMessage.getPayload());
        } catch (JsonProcessingException e) {
            messenger.sendError(session, "INVALID_JSON", "Message must be a valid JSON envelope");
            return;
        }
        String type = envelope.path("type").asText(null);
        JsonNode payload = envelope.path("payload");
        if (type == null || type.isBlank()) {
            messenger.sendError(session, "INVALID_JSON", "Envelope must contain a 'type' field");
            return;
        }
        dispatcher.dispatch(session, type, payload, messenger);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.leave(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessionRegistry.leave(session);
    }
}