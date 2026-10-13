package com.freightfox.chatapp.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Inbound WebSocket envelope: a {@code type} discriminator plus a free-form JSON
 * {@code payload} (mirrors the reference {@code broadcast-be} protocol).
 */
public record WsEnvelope(String type, JsonNode payload) {
}