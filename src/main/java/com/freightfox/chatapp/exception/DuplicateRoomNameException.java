package com.freightfox.chatapp.exception;

/**
 * Raised when a room name is already taken; mapped to {@code 409 Conflict}.
 */
public class DuplicateRoomNameException extends ChatException {

    public DuplicateRoomNameException(String roomName) {
        super("Chat room '" + roomName + "' already exists");
    }
}