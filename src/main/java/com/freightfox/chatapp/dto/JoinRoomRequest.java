package com.freightfox.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for joining a room; the participant name is added to the room's Redis Set.
 */
public record JoinRoomRequest(
        @NotBlank(message = "participant must not be blank") String participant
) {
}