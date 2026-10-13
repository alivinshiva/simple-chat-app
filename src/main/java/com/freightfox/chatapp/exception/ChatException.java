package com.freightfox.chatapp.exception;

/**
 * Base domain exception. Subclasses carry the specific chat-room failure; the
 * {@link GlobalExceptionHandler} maps them to HTTP problem details.
 */
public class ChatException extends RuntimeException {

    public ChatException(String message) {
        super(message);
    }
}