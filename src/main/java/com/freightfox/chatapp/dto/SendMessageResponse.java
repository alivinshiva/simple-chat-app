package com.freightfox.chatapp.dto;

/**
 * Success body for sending a message.
 */
public record SendMessageResponse(String message, String status) {
}