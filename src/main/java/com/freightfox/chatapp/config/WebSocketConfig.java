package com.freightfox.chatapp.config;

import com.freightfox.chatapp.ws.RealtimeChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the real-time chat WebSocket handler at {@code /ws/chat}. Origins are left
 * open so the service stays Postman/ws-client testable; authentication is out of scope
 * for this assignment.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatProperties properties;
    private final RealtimeChatWebSocketHandler handler;

    public WebSocketConfig(ChatProperties properties, RealtimeChatWebSocketHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.wsEndpoint())
                .setAllowedOriginPatterns("*");
    }
}