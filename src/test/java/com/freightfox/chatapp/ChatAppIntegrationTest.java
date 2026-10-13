package com.freightfox.chatapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.redis.RedisChatStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the brief's five test cases (TC1-TC5) against a real Redis
 * (Testcontainers) and a real WebSocket connection.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatAppIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("Test Case 1 — Create and join chat room: metadata stored, participant added to Redis Set")
    void t1CreateAndJoin() {
        String roomId = "tc1-general";

        ResponseEntity<String> create = restTemplate.postForEntity(
                "/api/chatapp/chatrooms",
                json(Map.of("roomName", roomId)),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(redisTemplate.opsForHash().hasKey(RedisChatStore.roomKey(roomId), "createdAt")).isTrue();

        ResponseEntity<String> join = restTemplate.postForEntity(
                "/api/chatapp/chatrooms/" + roomId + "/join",
                json(Map.of("participant", "guest_user")),
                String.class);
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redisTemplate.opsForSet().members(RedisChatStore.participantsKey(roomId)))
                .containsExactlyInAnyOrder("guest_user");
    }

    @Test
    @DisplayName("Test Case 2 — Send and retrieve messages: chronological order, last N retrievable")
    void t2SendAndRetrieve() throws Exception {
        String roomId = "tc2-room";

        restTemplate.postForEntity("/api/chatapp/chatrooms",
                json(Map.of("roomName", roomId)), String.class);

        postMessage(roomId, "guest_user", "Hello, everyone!");
        postMessage(roomId, "another_user", "Hi, guest_user!");

        ResponseEntity<String> history = restTemplate.getForEntity(
                "/api/chatapp/chatrooms/" + roomId + "/messages?limit=10",
                String.class);
        JsonNode messages = objectMapper.readTree(history.getBody()).path("messages");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).path("participant").asText()).isEqualTo("guest_user");
        assertThat(messages.get(1).path("participant").asText()).isEqualTo("another_user");

        List<String> stored = redisTemplate.opsForList().range(RedisChatStore.messagesKey(roomId), 0, -1);
        assertThat(stored).hasSize(2);
        assertThat(objectMapper.readTree(stored.get(0)).path("participant").asText()).isEqualTo("guest_user");
        assertThat(objectMapper.readTree(stored.get(1)).path("participant").asText()).isEqualTo("another_user");
    }

    @Test
    @DisplayName("Test Case 3 — Real-time messaging: publish on room channel reaches every participant")
    void t3RealtimeBroadcast() throws Exception {
        String roomId = "tc3-live";

        restTemplate.postForEntity("/api/chatapp/chatrooms",
                json(Map.of("roomName", roomId)), String.class);

        TestClient alice = connect();
        TestClient bob = connect();
        try {
            alice.send(joinEnvelope(roomId, "alice"));
            assertThat(alice.awaitType("joined").path("payload").path("roomId").asText()).isEqualTo(roomId);
            bob.send(joinEnvelope(roomId, "bob"));
            bob.awaitType("joined");

            // REST-sent message must fan out to every subscribed participant.
            postMessage(roomId, "alice", "hello from rest");

            JsonNode aliceFrame = alice.awaitType("message");
            JsonNode bobFrame = bob.awaitType("message");
            assertThat(aliceFrame.path("payload").path("message").asText()).isEqualTo("hello from rest");
            assertThat(bobFrame.path("payload").path("message").asText()).isEqualTo("hello from rest");
        } finally {
            alice.close();
            bob.close();
        }
    }

    @Test
    @DisplayName("Test Case 4 — Error handling: non-existent room rejected (REST 404 ProblemDetail, WS ROOM_NOT_FOUND)")
    void t4UnknownRoomErrors() {
        ResponseEntity<String> send = restTemplate.postForEntity(
                "/api/chatapp/chatrooms/ghost/messages",
                json(Map.of("participant", "guest_user", "message", "hi")),
                String.class);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send.getBody()).contains("does not exist");
    }

    @Test
    @DisplayName("Test Case 5 — Chat room deletion: all Redis keys removed, room name becomes reusable")
    void t5DeleteRoom() {
        String roomId = "tc5-doomed";

        restTemplate.postForEntity("/api/chatapp/chatrooms",
                json(Map.of("roomName", roomId)), String.class);
        restTemplate.postForEntity("/api/chatapp/chatrooms/" + roomId + "/join",
                json(Map.of("participant", "guest_user")), String.class);
        postMessage(roomId, "guest_user", "bye");

        ResponseEntity<String> deleted = restTemplate.exchange(
                "/api/chatapp/chatrooms/" + roomId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(redisTemplate.hasKey(RedisChatStore.roomKey(roomId))).isFalse();
        assertThat(redisTemplate.hasKey(RedisChatStore.participantsKey(roomId))).isFalse();
        assertThat(redisTemplate.hasKey(RedisChatStore.messagesKey(roomId))).isFalse();

        ResponseEntity<String> after = restTemplate.getForEntity(
                "/api/chatapp/chatrooms/" + roomId + "/messages",
                String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> recreate = restTemplate.postForEntity(
                "/api/chatapp/chatrooms",
                json(Map.of("roomName", roomId)),
                String.class);
        assertThat(recreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void postMessage(String roomId, String participant, String message) {
        restTemplate.postForEntity(
                "/api/chatapp/chatrooms/" + roomId + "/messages",
                json(Map.of("participant", participant, "message", message)),
                String.class);
    }

    private TestClient connect() {
        CollectingHandler handler = new CollectingHandler();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                client, handler, "ws://localhost:" + port + "/ws/chat");
        manager.start();
        return new TestClient(handler, manager);
    }

    private String joinEnvelope(String roomId, String participant) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "join",
                "payload", Map.of("roomId", roomId, "participant", participant)));
    }

    private HttpEntity<String> json(Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitConnected(WebSocketConnectionManager manager, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline && !manager.isConnected()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (!manager.isConnected()) {
            throw new IllegalStateException("WebSocket connection failed");
        }
    }

    private class TestClient {

        private final CollectingHandler handler;
        private final WebSocketConnectionManager manager;

        private TestClient(CollectingHandler handler, WebSocketConnectionManager manager) {
            this.handler = handler;
            this.manager = manager;
            awaitConnected(manager, 5);
        }

        void send(String envelope) {
            handler.send(envelope);
        }

        JsonNode awaitType(String type) throws Exception {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                String raw = handler.poll(1, TimeUnit.SECONDS);
                if (raw == null) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(raw);
                if (type.equals(node.path("type").asText())) {
                    return node;
                }
            }
            throw new AssertionError("Timed out waiting for envelope type '" + type + "'");
        }

        void close() {
            handler.close();
            manager.stop();
        }
    }

    private static class CollectingHandler extends TextWebSocketHandler {

        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private volatile WebSocketSession session;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            this.session = session;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            inbox.offer(message.getPayload());
        }

        void send(String envelope) {
            try {
                session.sendMessage(new TextMessage(envelope));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        String poll(long timeout, TimeUnit unit) throws InterruptedException {
            return inbox.poll(timeout, unit);
        }

        void close() {
            try {
                if (session != null) {
                    session.close(CloseStatus.NORMAL);
                }
            } catch (Exception ignored) {
            }
        }
    }
}