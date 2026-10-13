package com.freightfox.chatapp.exception;

/**
 * Raised when an operation targets a room that does not exist; mapped to
 * {@code 404 Not Found}.
 */
public class RoomNotFoundException extends ChatException {

    public RoomNotFoundException(String roomId) {
        super("Chat room '" + roomId + "' does not exist");
    }
}