package com.freightfox.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for creating a room. The name becomes both the room's display name and its
 * id, and must be unique.
 */
public record CreateRoomRequest(
        @NotBlank(message = "roomName must not be blank") String roomName
) {
}