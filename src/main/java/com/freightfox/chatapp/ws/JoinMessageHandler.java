package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.freightfox.chatapp.service.ChatRoomService;
import com.freightfox.chatapp.service.RealtimeSessionRegistry;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Handles {@code join}. Validates the room exists ({@code ROOM_NOT_FOUND}), registers
 * the socket against the room in {@link RealtimeSessionRegistry} (for delivery) and
 * the participant in the durable Redis Set (for membership), then acknowledges with a
 * {@code joined} frame.
 */
@Component
public class JoinMessageHandler implements WsMessageHandler {

    private final RealtimeSessionRegistry sessionRegistry;
    private final ChatRoomService chatRoomService;
    private final WsMessenger messenger;

    public JoinMessageHandler(RealtimeSessionRegistry sessionRegistry, ChatRoomService chatRoomService, WsMessenger messenger) {
        this.sessionRegistry = sessionRegistry;
        this.chatRoomService = chatRoomService;
        this.messenger = messenger;
    }

    @Override
    public String type() {
        return "join";
    }

    @Override
    public void handle(WebSocketSession session, JsonNode payload) {
        String roomId = payload.path("roomId").asText(null);
        String participant = payload.path("participant").asText("anonymous");
        if (roomId == null || roomId.isBlank()) {
            messenger.sendError(session, "INVALID_PAYLOAD", "roomId is required");
            return;
        }
        if (!chatRoomService.roomExists(roomId)) {
            messenger.sendError(session, "ROOM_NOT_FOUND", "Chat room '" + roomId + "' does not exist");
            return;
        }
        sessionRegistry.join(session, roomId);
        chatRoomService.joinRoom(roomId, participant);
        messenger.send(session, "joined", Map.of("roomId", roomId, "participant", participant));
    }
}