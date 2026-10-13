package com.freightfox.chatapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the chat application, bound from the {@code app.chat.*} prefix in
 * {@code application.yml}.
 *
 * @param wsEndpoint          WebSocket path clients connect to
 * @param historyDefaultLimit limit used when a history request omits {@code ?limit}
 * @param historyMaxLimit     upper bound applied to any requested limit
 * @param messageLogMax       per-list cap for a room's message history (oldest trimmed)
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        String wsEndpoint,
        int historyDefaultLimit,
        int historyMaxLimit,
        int messageLogMax) {
}