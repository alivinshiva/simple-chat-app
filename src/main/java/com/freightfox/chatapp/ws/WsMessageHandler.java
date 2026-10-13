package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.socket.WebSocketSession;

/**
 * Contract for one inbound WebSocket command type. Implementations are picked up by
 * {@link ChatCommandDispatcher} and keyed by {@link #type()}.
 */
public interface WsMessageHandler {

    String type();

    void handle(WebSocketSession session, JsonNode payload);
}