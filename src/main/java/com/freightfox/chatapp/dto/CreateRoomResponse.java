package com.freightfox.chatapp.dto;

/**
 * Success body for room creation; carries the assigned room id back to the caller.
 */
public record CreateRoomResponse(String message, String roomId, String status) {
}