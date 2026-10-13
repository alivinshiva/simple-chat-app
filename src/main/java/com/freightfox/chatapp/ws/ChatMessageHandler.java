package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.freightfox.chatapp.service.ChatRoomService;
import com.freightfox.chatapp.service.RealtimeSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Handles {@code chat}. A socket must have joined first ({@code NOT_IN_ROOM}), then the
 * message is sent through the single write path in {@link ChatRoomService}; delivery
 * back to participants (including the sender, as an echo) happens via Redis pub/sub.
 */
@Component
public class ChatMessageHandler implements WsMessageHandler {

    private final RealtimeSessionRegistry sessionRegistry;
    private final ChatRoomService chatRoomService;
    private final WsMessenger messenger;

    public ChatMessageHandler(RealtimeSessionRegistry sessionRegistry, ChatRoomService chatRoomService, WsMessenger messenger) {
        this.sessionRegistry = sessionRegistry;
        this.chatRoomService = chatRoomService;
        this.messenger = messenger;
    }

    @Override
    public String type() {
        return "chat";
    }

    @Override
    public void handle(WebSocketSession session, JsonNode payload) {
        String roomId = sessionRegistry.roomOf(session);
        if (roomId == null) {
            messenger.sendError(session, "NOT_IN_ROOM", "You must join a room first");
            return;
        }
        String participant = payload.path("participant").asText("anonymous");
        String message = payload.path("message").asText(null);
        if (message == null || message.isBlank()) {
            messenger.sendError(session, "INVALID_PAYLOAD", "message is required");
            return;
        }
        chatRoomService.sendMessage(roomId, participant, message);
    }
}