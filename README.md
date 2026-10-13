# Simple Chat Application Using Redis

A **Spring Boot 3 REST + WebSocket** chat service backed by **Redis**. Restart/multi-instance
safe — Redis is the durable store, in-memory sockets are only the live delivery layer.

## Features

- **REST API** — create room, join room, post message, read message history.
- **WebSocket** (`/ws/chat`) — join a room, then chat. Messages are broadcast live to everyone in the
  room (including the sender). REST posts reach WebSocket listeners instantly and vice
  versa — **one write path** (append to Redis list → pub/sub → fan-out).

## Tech stack

| Layer | Choice |
|-------|--------|
| Language / runtime | Java 17 (works on Java 24) |
| Framework | Spring Boot 3.5 |
| Data store | Redis 7 (`Hash`/`Set`/`List`/PubSub) |
| Realtime | Spring WebSocket (`TextWebSocketHandler`) |

## Project layout

```
chat-app
├── docker-compose.yml                 # Redis 7 for local dev (AOF on)
├── Dockerfile + render.yaml           # one-click public deployment (see below)
├── pom.xml
└── src
    ├── main/java/com/freightfox/chatapp
    │   ├── ChatAppApplication.java
    │   ├── config/        ChatProperties, OpenApiConfig, RedisConfig, WebSocketConfig
    │   ├── controller/    ChatRoomController (REST endpoints), RootController (landing)
    │   ├── dto/           records for requests/responses, MessageDto
    │   ├── exception/     ChatException hierarchy + GlobalExceptionHandler
    │   ├── redis/         RedisChatStore (Facade), RedisMessageSubscriber
    │   ├── service/       ChatRoomService (single write path), RealtimeSessionRegistry
    │   └── ws/            handler, command dispatcher, join/chat handlers, messenger
    └── test/java/com/freightfox/chatapp
        ├── ChatRoomServiceTest        unit
        ├── ChatRoomControllerTest     MockMvc
        ├── RootControllerTest         MockMvc (landing page)
        └── ChatAppIntegrationTest     Testcontainers + real WebSocket
```

## Prerequisites

- JDK 17+ and Maven 3.9+
- A running Docker engine for tests and local dev (Testcontainers / `docker compose`)

## Setup & Run

```bash
# 1. Start Redis
docker compose up -d

# 2. Run the app (defaults to localhost:6379)
mvn spring-boot:run
# → REST on http://localhost:8080, WebSocket on ws://localhost:8080/ws/chat
```

Override config via env / args if needed:

```bash
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
```

## How to use the app

Once the app is running (above), you have three ways to drive it:

**1. Swagger UI (recommended for testing)** — opens an interactive console for every endpoint:

| URL | What it gives you |
|-----|-------------------|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI — click "Try it out", fill a request, see the response |
| `http://localhost:8080/v3/api-docs` | The raw OpenAPI JSON spec |

> After a Render deploy the same two URLs are
> `https://<your-app>.onrender.com/swagger-ui.html` and
> `https://<your-app>.onrender.com/v3/api-docs`.

**2. One-command smoke test** — exercises every endpoint (+ error cases) and real WebSocket fan-out:

```bash
./test-api.sh
```

Prints `PASS`/`FAIL` per check and exits `0` only if everything passes.

**3. Postman collection** — import `postman/chat-app.postman_collection.json`, set `baseUrl`
(default `http://localhost:8080`), and run the requests in order. Every request's description states
its expected status (201/200/400/404/409).

Typical flow (also what TC1–TC5 do):

```bash
# create a room
curl -X POST http://localhost:8080/api/chatapp/chatrooms -H 'Content-Type: application/json' -d '{"roomName":"general"}'
# join it
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/join -H 'Content-Type: application/json' -d '{"participant":"sandy"}'
# send a message
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/messages -H 'Content-Type: application/json' -d '{"participant":"sandy","message":"hello"}'
# read history
curl http://localhost:8080/api/chatapp/chatrooms/general/messages
```

For the full step-by-step manual walkthrough (including WebSocket via `wscat`), see **`TESTING.md`**.

## Tests

```bash
mvn test
```

29 tests: 11 service unit tests, 12 controller MockMvc tests, 1 landing-page test, and 5
end-to-end integration tests (Testcontainers + real WebSocket clients) mapped one-to-one to the
brief's test cases.

> **Docker note:** if your daemon enforces a minimum API version (e.g. OrbStack ≥ 1.40) while
> testcontainers negotiates down to docker-java 1.32, set `docker.api.version` to your daemon's
> version — the pom already does this via surefire (`<api.version>1.45</api.version>`).

## Public deployment (Render one-click)

The repo ships a `Dockerfile` and a `render.yaml` blueprint. Connect **https://github.com/alivinshiva/simple-chat-app**
in the Render dashboard (New → Blueprint) and fill two env vars for a hosted Redis (e.g. a free
[Upstash](https://upstash.com) instance):

| Env var | Value |
|---------|-------|
| `REDIS_HOST` | your Redis host (e.g. `your-key.upstash.io`) |
| `REDIS_PORT` | `6379` (Upstash TLS: `6380`) |
| `SPRING_DATA_REDIS_PASSWORD` | your Redis password |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `true` if TLS is on (Upstash) |

Once deployed, the form's two URLs are:

- Public API: `https://<your-app>.onrender.com/`
- Swagger UI: `https://<your-app>.onrender.com/swagger-ui.html`
- OpenAPI spec: `https://<your-app>.onrender.com/v3/api-docs`

## REST API

All bodies are JSON. Errors are RFC 7807 `application/problem+json`.

### 1. Create a chat room

```
POST /api/chatapp/chatrooms
```

Request:

```json
{ "roomName": "general" }
```

Responses:

```json
// 201 Created
{ "message": "Chat room 'general' created successfully.", "roomId": "general", "status": "success" }

// 409 Conflict (duplicate) , also 400 for blank roomName
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Chat room 'general' already exists"
}
```

### 2. Join a chat room

```
POST /api/chatapp/chatrooms/{roomId}/join
```

```json
{ "participant": "sandy" }
```

```json
// 200 OK — also 404 if the room does not exist
{ "message": "User 'sandy' joined chat room 'general'.", "status": "success" }
```

### 3. Send a message (via REST)

```
POST /api/chatapp/chatrooms/{roomId}/messages
```

```json
{ "participant": "sandy", "message": "hello" }
```

```json
// 200 OK — also 400 for blank message, 404 for missing room
{ "message": "Message sent successfully.", "status": "success" }
```

### 4. Get message history (newest-last)

```
GET /api/chatapp/chatrooms/{roomId}/messages?limit=10
```

`limit` optional (default 10, max 100, positive integer).

```json
// 200 OK — also 404 for missing room, 400 for limit <= 0
{
  "messages": [
    { "participant": "sandy", "message": "hello", "timestamp": "2026-10-13T10:00:00Z" }
  ]
}
```

### 5. Delete a chat room (brief test case 5)

```
DELETE /api/chatapp/chatrooms/{roomId}
```

Removes the room's Redis Hash, Set and List keys; the name can then be reused.

```json
// 200 OK — also 404 if the room does not exist
{ "message": "Chat room 'general' deleted successfully.", "status": "success" }
```


## How the realtime flow works

```
POST /messages  ─┐
                 ├→ ChatRoomService.sendMessage ── RPUSH chat:room:{id}:messages ──┐
WS chat frame  ──┘                            (persist + cap at messageLogMax)     │
                                                                                    ▼
                                                                        PUBLISH chat.room.{id} ─┐
                                                                                 │               │
                                                                                 ▼               ▼
                                                                   RedisMessageSubscriber  (listener on chat.room.*)
                                                                                 │
                                                                                 ▼
                                                                   RealtimeSessionRegistry (live sockets)
                                                                                 │
                                                                                 ▼
                                                     fan-out: {type:"message", payload:{...}} to every socket in the room
```

The sender's room-membership lookup lives in the in-memory registry; the durable member list is the
Redis `participants` set — so a socket disconnect does not lose room membership for REST history, and
a multi-instance deployment shares one write path through Redis.