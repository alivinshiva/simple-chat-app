package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Decides what to do with an inbound WebSocket envelope — a small <b>Command</b>
 * pattern: each supported message type maps to one {@link WsMessageHandler}, and an
 * unknown type gets a typed {@code UNKNOWN_TYPE} error instead of failing the socket.
 */
@Component
public class ChatCommandDispatcher {

    private final Map<String, WsMessageHandler> handlers;

    public ChatCommandDispatcher(List<WsMessageHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(WsMessageHandler::type, Function.identity()));
    }

    public void dispatch(WebSocketSession session, String type, JsonNode payload, WsMessenger messenger) {
        WsMessageHandler handler = handlers.get(type);
        if (handler == null) {
            messenger.sendError(session, "UNKNOWN_TYPE", "Unknown message type '" + type + "'");
            return;
        }
        handler.handle(session, payload);
    }
}