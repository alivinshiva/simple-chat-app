package com.freightfox.chatapp.controller;

import com.freightfox.chatapp.dto.CreateRoomRequest;
import com.freightfox.chatapp.dto.CreateRoomResponse;
import com.freightfox.chatapp.dto.DeleteRoomResponse;
import com.freightfox.chatapp.dto.HistoryResponse;
import com.freightfox.chatapp.dto.JoinRoomRequest;
import com.freightfox.chatapp.dto.JoinRoomResponse;
import com.freightfox.chatapp.dto.SendMessageRequest;
import com.freightfox.chatapp.dto.SendMessageResponse;
import com.freightfox.chatapp.service.ChatRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for chat rooms ({@code /api/chatapp/chatrooms*}).
 *
 * <p>Endpoints: create room, join room, send message, read history, delete room.
 * Every mutating call funnels through {@link ChatRoomService}, the single write path
 * shared with the WebSocket layer. Errors are reported as RFC 7807
 * {@code application/problem+json} via {@link GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/chatapp/chatrooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    public ChatRoomController(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        String roomId = chatRoomService.createRoom(request.roomName());
        return new CreateRoomResponse(
                "Chat room '" + roomId + "' created successfully.",
                roomId,
                "success");
    }

    @PostMapping("/{roomId}/join")
    public JoinRoomResponse joinRoom(@PathVariable String roomId, @Valid @RequestBody JoinRoomRequest request) {
        chatRoomService.joinRoom(roomId, request.participant());
        return new JoinRoomResponse(
                "User '" + request.participant() + "' joined chat room '" + roomId + "'.",
                "success");
    }

    @PostMapping("/{roomId}/messages")
    public SendMessageResponse sendMessage(@PathVariable String roomId, @Valid @RequestBody SendMessageRequest request) {
        chatRoomService.sendMessage(roomId, request.participant(), request.message());
        return new SendMessageResponse("Message sent successfully.", "success");
    }

    @GetMapping("/{roomId}/messages")
    public HistoryResponse getMessages(@PathVariable String roomId, @RequestParam(required = false) Integer limit) {
        return new HistoryResponse(chatRoomService.getMessages(roomId, limit));
    }

    @DeleteMapping("/{roomId}")
    public DeleteRoomResponse deleteRoom(@PathVariable String roomId) {
        chatRoomService.deleteRoom(roomId);
        return new DeleteRoomResponse(
                "Chat room '" + roomId + "' deleted successfully.",
                "success");
    }
}