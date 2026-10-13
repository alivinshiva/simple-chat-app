package com.freightfox.chatapp.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal landing page for the deployed service: exposes navigation to the REST API, the Swagger
 * UI, the OpenAPI spec and the WebSocket endpoint. Also serves as the deployment health check.
 */
@RestController
public class RootController {

    @Value("${app.chat.ws-endpoint:/ws/chat}")
    private String wsEndpoint;

    @GetMapping({"/", ""})
    public Map<String, String> index() {
        return Map.of(
                "name", "Simple Chat Application",
                "restApi", "/api/chatapp/chatrooms",
                "swaggerUi", "/swagger-ui.html",
                "openApiSpec", "/v3/api-docs",
                "webSocket", wsEndpoint);
    }
}