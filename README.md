# Simple Chat Application Using Redis

A backend interview-assignment implementation for FreightFox AI: a **Spring Boot 3 REST + WebSocket**
chat service backed by **Redis**. Restart/multi-instance safe — Redis is the durable store, in-memory
sockets are only the live delivery layer.

Read `docs/DESIGN.md` for the full design (Redis structures, data flow, error contract, edge cases,
scaling story) and `BUILD_PLAN.md` for the build log. For copy-paste curl commands covering every
route and error case, see **`TESTING.md`**. For a one-command smoke test of the whole API (**`./test-api.sh`**)
or a ready-to-import **Postman collection** (`postman/chat-app.postman_collection.json`), see
below.

## Features

- **REST API** — create room, join room, post message, read message history.
- **WebSocket** (`/ws/chat`) — join a room, then chat. Messages are broadcast live to everyone in the
  room (including the sender as an echo). REST posts reach WebSocket listeners instantly and vice
  versa — **one write path** (append to Redis list → pub/sub → fan-out).
- **RFC 7807 `ProblemDetail`** errors on REST, typed `error` envelopes on WebSocket.
- Message history is durable in Redis (capped, `messageLogMax`).

## Tech stack

| Layer | Choice |
|-------|--------|
| Language / runtime | Java 17 (works on Java 24) |
| Framework | Spring Boot 3.5 |
| Data store | Redis 7 (`Hash`/`Set`/`List`/PubSub) |
| Realtime | Spring WebSocket (`TextWebSocketHandler`) |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers (`redis:7-alpine`) |

## Project layout

```
chat-app
├── docker-compose.yml                 # Redis 7 for local dev (AOF on)
├── Dockerfile + render.yaml           # one-click public deployment (see below)
├── pom.xml
├── test-api.sh                        # one-command end-to-end smoke test
├── postman/chat-app.postman_collection.json  # import into Postman
├── docs/DESIGN.md                     # full design: structures, flow, error contract
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

## Quick smoke test (no copy-paste)

With the app running, one command exercises every endpoint and error case plus real WebSocket
fan-out, and prints `PASS`/`FAIL`:

```bash
./test-api.sh
```

It uses its own temporary room, asserts each status code and response body, and cleans up after
itself (exit code `0` only if every check passes).

## Postman

Import `postman/chat-app.postman_collection.json` (Collection → Import). Each request is pre-filled
with the correct method, path (uses `{{baseUrl}}`, default `http://localhost:8080`) and body, and
describes its expected status code (TC1–TC5). The manual `TESTING.md` covers equivalent raw `curl`
commands if you prefer.

## Tests

```bash
mvn test
```

28 tests: 11 service unit tests, 12 controller MockMvc tests, 5 end-to-end integration tests
mapped one-to-one to the brief's test cases, and 1 root-landing test. The integration suite spins
up real Redis with Testcontainers and drives real WebSocket clients, asserting on Redis state
(room Hash, participant Set, message List) as well as HTTP/WS behaviour.

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

### Postman walkthrough

1. `POST http://localhost:8080/api/chatapp/chatrooms` with `{"roomName":"general"}` → 201.
2. `POST http://localhost:8080/api/chatapp/chatrooms/general/messages` with
   `{"participant":"sandy","message":"hi from postman"}` → 200.
3. `GET http://localhost:8080/api/chatapp/chatrooms/general/messages` → the message appears.
4. Do the same with a second participant to build a conversation.
5. `DELETE http://localhost:8080/api/chatapp/chatrooms/general` → 200, then step 3 returns 404.

## WebSocket

Endpoint: `ws://localhost:8080/ws/chat`

Envelope format (same as the reference `broadcast-be` client):

```json
{ "type": "join", "payload": { "roomId": "general", "participant": "alice" } }
{ "type": "chat", "payload": { "participant": "alice", "message": "hello" } }
```

Server frames:

| Type | When | Payload |
|------|------|---------|
| `joined` | successful join | `{ "roomId", "participant" }` |
| `message` | every message in your room (own echo included) | `{ "participant", "message", "timestamp" }` |
| `error` | any failure | `{ "code", "message" }` |

Error codes: `INVALID_JSON` (malformed envelope / missing `type`), `UNKNOWN_TYPE`,
`INVALID_PAYLOAD` (missing `roomId` on join or blank message on chat), `NOT_IN_ROOM` (chat before
join), `ROOM_NOT_FOUND` (join a room that does not exist).

### WebSocket client example (`wscat`)

```bash
npm install -g wscat
wscat -c ws://localhost:8080/ws/chat

# in the terminal:
{"type":"join","payload":{"roomId":"general","participant":"alice"}}
{"type":"chat","payload":{"participant":"alice","message":"hello"}}
```

Open a second `wscat` to the same room to watch messages fan out to you live.

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