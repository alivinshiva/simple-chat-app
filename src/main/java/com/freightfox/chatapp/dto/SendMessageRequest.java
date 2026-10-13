package com.freightfox.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for sending a chat message.
 */
public record SendMessageRequest(
        @NotBlank(message = "participant must not be blank") String participant,
        @NotBlank(message = "message must not be blank") String message
) {
}