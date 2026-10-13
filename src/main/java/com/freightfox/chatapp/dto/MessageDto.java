package com.freightfox.chatapp.dto;

/**
 * A single chat message. Serialized to JSON for the Redis List and used verbatim as
 * the {@code payload} of real-time {@code message} frames.
 */
public record MessageDto(String participant, String message, String timestamp) {
}