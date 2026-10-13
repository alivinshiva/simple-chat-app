package com.freightfox.chatapp.dto;

import java.util.List;

/**
 * Message history body, newest-last up to the requested/clamped limit.
 */
public record HistoryResponse(List<MessageDto> messages) {
}