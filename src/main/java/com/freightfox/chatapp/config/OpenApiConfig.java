package com.freightfox.chatapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata. Exposes the collection's contract at GET /v3/api-docs and the
 * interactive console at /swagger-ui.html (also served by the deployed app).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI chatAppOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Simple Chat Application — REST API")
                .description(
                        "Chat rooms backed by Redis. Create / join / message rooms over REST; real-time "
                        + "broadcasts flow over WebSocket (ws://host:8080/ws/chat). Errors use RFC 7807 "
                        + "Problem Details. Test case 5 (room deletion) is included.")
                .version("1.0.0")
                .contact(new Contact().name("Chat App").email("alivin612003@gmail.com")));
    }
}