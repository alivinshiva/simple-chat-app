package com.freightfox.chatapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Simple Chat Application Using Redis.
 *
 * <p>One Spring Boot process exposing two doors over a single Redis-backed core:
 * a REST API ({@code /api/chatapp/chatrooms*}) for CRUD and message history, and a
 * WebSocket endpoint ({@code /ws/chat}) for real-time messaging. See
 * {@code docs/DESIGN.md} for the full design.</p>
 */
@SpringBootApplication
public class ChatAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatAppApplication.class, args);
    }
}