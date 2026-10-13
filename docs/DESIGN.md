# Chat App — System Design (Discussion Record)

Assignment: *Simple Chat Application Using Redis* — Spring Boot, REST-only APIs, Postman-testable,
Redis as primary store (Hash / Set / List / Pub/Sub), optional realtime via WebSocket.

This document captures the collaborative design discussion (2-dev format) so decisions are
traceable and no rationale gets lost. It is the source of truth for the implementation.

---

## 1. System overview

**One Spring Boot application, two doors, Redis as the brain.**

```
REST (HTTP)  ──┐
               ├─▶ Spring Boot (one process) ──▶ Redis (Hash + Set + List + Pub/Sub)
WebSocket (WS) ─┘
```

- **REST door** (`/api/chatapp/chatrooms*`): create room, join, send message, fetch history, delete
  room (brief's optional test case 5).
  Request/response semantics.
- **WS door** (`/ws/chat`): join a room and exchange messages live. Event-driven semantics.
- **Redis** holds all durable state; in-memory state exists only for live socket delivery.

Event-driven shape: the sender is the **event producer**, the room's connected clients are the
**consumers**, and Redis Pub/Sub is the **event bus** that fans one publish out to everyone
subscribed to the room's topic (sender included — echo, matching the existing TypeScript app).

**Key nuance (interview-ready):** WebSocket clients cannot subscribe to Redis directly. The
*publisher* is the app itself (acting for the sender); the *subscribers* are app instances; each
instance then relays to the WebSocket sessions it holds in that room.

---

## 2. Redis data structures (brief-mandated)

| Structure | Key | Holds |
|---|---|---|
| Hash | `chat:room:{roomId}` | room metadata: `name`, `createdAt` |
| Set | `chat:room:{roomId}:participants` | durable member list (survives disconnect) |
| List | `chat:room:{roomId}:messages` | message history, JSON strings, chronological (RPUSH) |
| Pub/Sub | channel `chat.room.{roomId}` | realtime event bus |

Message JSON shape (stored verbatim in the List and carried on the channel):
```json
{ "participant": "guest_user", "message": "Hello, everyone!", "timestamp": "2024-01-01T10:00:00Z" }
```

---

## 3. Locked decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **One process, two doors** (REST + WS in the same Spring Boot app). No second service. | Brief is a single service; fan-out is achieved via Redis Pub/Sub, not a separate process. |
| 2 | **One unified write path.** A message — from REST *or* WebSocket — always does: append to List → publish to channel. Subscriber fans out to room's WS sessions. | One behavior to reason about and test; REST-sent messages reach live WS subscribers. |
| 3 | **Per-room channels** `chat.room.{roomId}`, subscriber listens on pattern `chat.room.*`, every published message carries `roomId`. | Room boundaries = topic boundaries; enables selective per-room subscriptions under horizontal scaling; per-room ops/debugging. |
| 4 | **Two distinct state stores.** Redis Set = durable member roster (join survives disconnect). In-memory `RealtimeSessionRegistry` = only *live* WS sockets, for delivery. | A closed socket cannot receive; Redis cannot push to sockets; the brief requires the Set. Don't collapse these. |
| 5 | **Error body = RFC 7807 `ProblemDetail`** on every REST error. | Modern Spring standard, zero custom error-shape code, Postman-friendly. |
| 6 | **Edge-case behavior** as specified in §5. | Falls out of decisions 1–5; no special tricks. |

---

## 4. Data flow (single write path)

```
sender (REST POST or WS "chat")
  → ChatRoomService.sendMessage(roomId, participant, message)
      → ensure room exists                 (else 404 / WS ROOM_NOT_FOUND)
      → MessageDto{participant, message, timestamp=now}
      → RedisChatStore.pushMessage()       (RPUSH + trim to cap)
      → RedisChatStore.publish()           (convertAndSend channel chat.room.{roomId})
      → [async] RedisMessageSubscriber.onMessage
          → parse roomId from channel
          → for each open session in RealtimeSessionRegistry.sessions(roomId):
              send { "type":"message", "payload": {participant, message, timestamp} }
```

WS side effects:
- `join` → validate room, register socket against room in registry, `SADD` participant, reply `joined`.
- `chat` → require socket already in a room, then the single write path above (echo is delivered via the subscriber).
- `close` / transport error → deregister socket from the registry only.

---

## 5. Error handling

**REST errors — every failure is caught in one place (`GlobalExceptionHandler`) and returned as RFC 7807 `ProblemDetail`:**

| Problem | Status | Detail |
|---|---|---|
| Blank `roomName` / `participant` / `message`; malformed body | 400 | `field: message` |
| `limit` ≤ 0 | 400 | `limit must be a positive integer` |
| Duplicate room name on create | 409 | `Chat room '…' already exists` |
| Join / send / history / delete on missing room | 404 | `Chat room '…' does not exist` |
| Any unexpected failure / Redis down | 500 | from exception |

Validation runs **before** any logic touches Redis.

**WebSocket errors — same envelope protocol, typed codes (no HTTP status on an open socket):**

```json
{ "type": "error", "payload": { "code": "…", "message": "…" } }
```

| Code | Meaning |
|---|---|
| `INVALID_JSON` | envelope unparseable, or missing `type` |
| `UNKNOWN_TYPE` | `type` is not `join` / `chat` |
| `NOT_IN_ROOM` | `chat` sent before `join` (from the original TS app) |
| `ROOM_NOT_FOUND` | `join` of a non-existent room (socket stays unregistered) |

**WS success is implicit:** a successful chat has no ack — the sender proves it by seeing their own
echo. Truth always recoverable via `GET /messages`.

---

## 6. Edge cases & state rules

| Case | Behavior |
|---|---|
| Same participant joins twice | Set is idempotent (`SADD`) — no duplicate, no error |
| Socket joins room A then room B | Registry moves the socket — receives only B |
| Client disconnects / dies mid-message | Socket removed from registry; member **stays** in Set; history intact |
| Send to room with 0 live sockets | Saves to List; broadcast reaches nobody (fine) |
| Concurrent senders | `RPUSH` is atomic + ordered — arrival order preserved |
| `limit` > available history | Return whatever exists (no error) |
| Room deletion | `DELETE /{roomId}` removes Hash + Set + List keys (brief's optional TC5 — implemented); missing room → 404; name becomes reusable |

---

## 7. Scaling pattern (interview story)

Redis Pub/Sub **decouples "who wrote the message" from "who holds the sockets"** — the whole point
vs. in-process direct fan-out. Under horizontal scaling (N app instances), each instance holds a
subset of the live WebSocket connections. A REST message landing on instance #2 still reaches
sockets held by instance #3 because every instance's subscriber hears the channel and fans out to
the sockets it holds. Upgrade path when message volume grows: switch from wildcard `chat.room.*`
subscription to **selective per-room subscriptions** so an instance is only woken for rooms it has
live listeners in.

**Trade-off (by design, not a bug):** Pub/Sub is fire-and-forget — a down subscriber misses that
live push. Durability lives in the List; clients "catch up" via `GET /messages` on (re)connect.

---

## 8. Build blueprint

```
chat-app/
├── pom.xml
├── docker-compose.yml          redis:7-alpine + AOF persistence
├── src/main/java/com/freightfox/chatapp/
│   ├── ChatAppApplication.java
│   ├── config/                 ChatProperties, RedisConfig, WebSocketConfig
│   ├── controller/             ChatRoomController (REST door — 4 endpoints)
│   ├── dto/                    request/response records
│   ├── exception/              ChatException hierarchy + GlobalExceptionHandler (ProblemDetail)
│   ├── redis/                  RedisChatStore (Facade) + RedisMessageSubscriber
│   ├── service/                ChatRoomService + RealtimeSessionRegistry
│   └── ws/                     RealtimeChatWebSocketHandler, ChatCommandDispatcher (Command),
│                               Join/Chat handlers, WsEnvelope, WsMessenger, WsMessageHandler
├── src/test/java/com/freightfox/chatapp/
│   ├── ChatRoomServiceTest       unit (mocked RedisChatStore)
│   ├── ChatRoomControllerTest    @WebMvcTest + MockMvc (400/404/409)
│   └── ChatAppIntegrationTest    Testcontainers Redis + real WebSocketClient (full flow)
└── README.md
```

Design patterns chosen to satisfy the brief's grading criteria:
- **Command** — WS message types map to `WsMessageHandler` beans (join/chat)
- **Facade** — `RedisChatStore` owns all key naming + Redis ops in one place
- **DTO separation** — request/response records independent of stored format
- **Centralized error contract** — one `GlobalExceptionHandler` for the REST door

Build log / step tracking lives in `chat-app/BUILD_PLAN.md`.