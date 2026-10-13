# Chat App — Build Plan & Log

Implementation of **docs/DESIGN.md** (Simple Chat Application Using Redis — Spring Boot + REST +
WebSocket + Redis).

**How to use this file:** each step is completed in order; this file is updated **after every step**
with a short note of what was done and how it was verified. Design decisions are not re-litigated here —
see `docs/DESIGN.md`.

---

## Steps

| # | Step | Status |
|---|------|--------|
| 0 | Docs: DESIGN.md + this plan | ✅ done |
| 1 | Scaffold: `pom.xml`, `docker-compose.yml`, `application.yml`, main class | ✅ done |
| 2 | Domain layer: DTOs, exceptions, `GlobalExceptionHandler` (ProblemDetail) | ✅ done |
| 3 | Redis layer: `RedisChatStore` (Hash/Set/List) + `RedisMessageSubscriber` (Pub/Sub) | ✅ done |
| 4 | Service layer: `ChatRoomService` (single write path) + `RealtimeSessionRegistry` | ✅ done |
| 5 | WebSocket layer: handler, dispatcher (Command), join/chat handlers, messenger | ✅ done |
| 6 | REST controller: `ChatRoomController` (4 endpoints) + WebSocket config | ✅ done |
| 7 | Tests: `ChatRoomServiceTest` (9), `ChatRoomControllerTest` (10), `ChatAppIntegrationTest` (3) | ✅ done |
| 8 | README: setup, run, Postman + WebSocket testing | ✅ done |
| 9 | Full build, all tests green (Docker/OrbStack API 1.32→1.45 fixed via surefire) | ✅ done |
| 10 | Docs-required polish: comments, TC5 room deletion, tests labelled TC1–TC5 | ✅ done |

---

## Step 0 — Docs
- Created `docs/DESIGN.md` (system design, data flow, error contract, edge cases, scaling story).
- Created this plan.
- **Verify:** both files read correctly. ✅

---

## Step 1 — Scaffold
- **Done:** `pom.xml` (Spring Boot 3.5.3, Java 17, starters web/websocket/data-redis/validation,
  testcontainers 1.21.3), `docker-compose.yml` (redis:7-alpine, AOF on), `application.yml`
  (redis host/port env-overridable, `app.chat.*` tuned knobs), `ChatAppApplication`.
- Package skeleton created under `com.freightfox.chatapp`.
- **Verify:** `mvn -q compile` → BUILD SUCCESS.

## Step 2 — Domain layer
- **Done:** request/response records (`CreateRoom/JoinRoom/SendMessage/History/MessageDto`), exception
  hierarchy (`ChatException`, `RoomNotFoundException`, `DuplicateRoomNameException`), and
  `GlobalExceptionHandler` returning **RFC 7807 `ProblemDetail`** for 400/404/409 + fallbacks.
- **Verify:** `mvn -q compile` ✅

## Step 3 — Redis layer
- **Done:** `RedisChatStore` (Facade — keys `chat:room:{id}`, `:participants`, `:messages`;
  `HSETNX`-atomic create via `putIfAbsent`, `SADD`, `RPUSH`+trim, LRANGE, `convertAndSend()`),
  `RedisConfig` (String serializers + listener container on pattern `chat.room.*`),
  `RedisMessageSubscriber` (parses roomId from channel, fans out to registry's live sessions, drops
  closed sessions).
- **Verify:** `mvn -q compile` ✅

## Step 4 — Service layer
- **Done:** `ChatRoomService` — single write path (`ensureRoomExists → serialize → RPUSH → publish`),
  history with default/clamp/reject limit rules, upsert-safe create. `RealtimeSessionRegistry` —
  `ConcurrentHashMap` socket↔room; join moves socket between rooms; leave cleans up; `sessions()`
  returns live set. **Exactly Decision #2 (one write path, REST+WS)**.
- **Verify:** `mvn -q compile` ✅

## Step 5 — WebSocket layer
- **Done:** `WebSocketConfig` (`/ws/chat`, all origins), `RealtimeChatWebSocketHandler`
  (parse `{type,payload}` → `INVALID_JSON` on bad envelope → `ChatCommandDispatcher`),
  `ChatCommandDispatcher` (**Command** pattern, `UNKNOWN_TYPE` fallback), `JoinMessageHandler`
  (`ROOM_NOT_FOUND` guard, registers socket, `joined` ack), `ChatMessageHandler` (`NOT_IN_ROOM`
  guard, then single write path), `WsMessenger` (typed envelopes), `WsEnvelope`.
- **Verify:** `mvn -q compile` ✅

## Step 6 — REST controller
- **Done:** `ChatRoomController` — `POST /api/chatapp/chatrooms` (201), `POST /{roomId}/join`,
  `POST /{roomId}/messages`, `GET /{roomId}/messages?limit=` all `@Valid` + ProblemDetail errors.
- **Verify:** `mvn -q compile` ✅

## Step 7 — Tests
- **Done:** 22 tests, all green:
  - `ChatRoomServiceTest` (9, Mockito): create-returns/duplicate, join-missing-room, message
    serialize ⇒ `pushMessage` **and** `publish` receive the same JSON, history read/empty,
    limit clamp (999→100) + reject (`limit<=0`).
  - `ChatRoomControllerTest` (10, MockMvc): 201 create, blank-name 400, duplicate 409
    `ProblemDetail`, join ok / 404 missing, send ok / blank 400, history + 404, limit=0 → 400.
  - `ChatAppIntegrationTest` (3, Testcontainers `redis:7-alpine` + real WebSocket):
    create→join→WS chat→echo→REST chat→fan-out to BOTH sockets→history contains both;
    chat-before-join → `NOT_IN_ROOM`; duplicate room → 409.
- **Docker footnote (needed on this machine):** testcontainers 1.21.3's
  `DockerClientProviderStrategy.getClientForConfig` falls back to docker-java API **1.32**
  whenever the config's api version is unknown, and OrbStack rejects anything below **1.40**
  ("client version 1.32 is too old"). `DOCKER_API_VERSION` env is ignored (shaded docker-java
  reads only the `api.version` system property). Fix: surefire `<systemPropertyVariables>` sets
  `<api.version>1.45</api.version>` (from `docker.api.version` property) in the test JVM. Safe on
  any daemon (newer client version is always acceptable).
- **Verify:** `mvn test` → `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS ✅

## Step 8 — README
- **Done:** `README.md` — features, tech stack, layout, prerequisites, setup/run (`docker compose up`
  + `mvn spring-boot:run`), test instructions (+ Docker/OrbStack note), full REST reference with
  request/response JSON and error cases, Postman walkthrough, WebSocket protocol (envelope + `joined`
  / `message` / `error` frames + error codes) with `wscat` example, and an ASCII data-flow diagram.
- **Verify:** rendered and cross-checked against the running API contract. ✅

## Step 9 — Full build, Postman smoke, final checks
- **Done:**
  - `mvn -q clean package` → executable `target/chat-app-1.0.0.jar` (34.9 MB), all 22 tests green.
  - Live smoke against the packaged jar + `docker compose` Redis 7:
    - REST: create → 201; duplicate → 409 ProblemDetail; blank roomName → 400 ProblemDetail;
      join / send / history all correct; missing room → 404 ProblemDetail. ✅
    - WebSocket (Node native client): chat-before-join → `NOT_IN_ROOM`; alice joins, bob joins;
      alice chats → both receive the frame (alice echo + bob fan-out). ✅
    - Single write path confirmed live: the WS-sent message appears in REST history; Redis holds
      Hash `chat:room:general`, Set `:participants` (sandy/alice/bob), List `:messages` (len 2). ✅
  - App stopped after smoke; Redis left running for `mvn spring-boot:run`.
- **Verify:** `mvn test` + live REST/WS smoke all green. ✅

## Step 10 — Docs-required polish (brief re-read)
Re-checked the app against the PDF's evaluation criteria and closed the gaps it explicitly asks for:

1. **Proper code comments** — every main class now carries a JavaDoc header (key layout on
   `RedisChatStore`, error contract on `GlobalExceptionHandler`, write path on `ChatRoomService`,
   command pattern on the WS layer, etc.).
2. **Test case 5 (optional): Chat Room Deletion** — `DELETE /api/chatapp/chatrooms/{roomId}`
   removes the room's Redis Hash, Set and List keys (`RedisChatStore.deleteRoom`); missing room
   → 404. Covered in service, controller and integration tests.
3. **Tests mapped to the brief's test cases** — `ChatAppIntegrationTest` re-labelled as
   TC1–TC5 with `@DisplayName`, TC1 now asserts Redis Hash/Sets directly via the injected
   `RedisTemplate`, TC2 asserts List order, TC5 asserts keys are gone and the name is reusable.
- **Verify:** `mvn test` → `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS.

## Step 11 — One-command smoke test + Postman collection
Made the app trivially testable for an evaluator, per the brief's "Testable by Postman" criterion:

1. **`test-api.sh`** — single command, no copy-paste. Creates a unique temp room, asserts HTTP
   status **and** body for all 5 endpoints (success + error cases), drives two real WebSocket
   clients (chat-before-join → `NOT_IN_ROOM`, sender echo, cross-client fan-out), prints
   `PASS`/`FAIL`, cleans up the temp room, and exits non-zero on any failure. Needs the app up
   (`docker compose up -d` + `mvn spring-boot:run`) and Node ≥ 22 for the WS checks.
2. **Postman collection** (`postman/chat-app.postman_collection.json`) — v2.1 collection, import
   and click; every request pre-filled with method, `{{baseUrl}}`-based URL, body, and the
   expected status documented (TC1–TC5).
3. Docs updated: README (layout tree, "Quick smoke test" + "Postman" sections), this log.
- **Verify:** `./test-api.sh` → 16/16 REST checks green (201/409/400, 200/404/400, 200/404/400,
  200/200/404/400, 404/200/201) + 3/3 WebSocket checks green, exit code 0. `bash -n` and JSON
  parse both clean.

## Done