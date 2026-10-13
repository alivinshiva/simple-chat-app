# Chat App — Manual API Test

Every test is a standalone curl with the full URL. Copy, paste, run — the expected
return is shown under each command. Start the app first:

```bash
cd chat-app
docker compose up -d
mvn spring-boot:run
```

---

## 1) Create Room — `POST /api/chatapp/chatrooms`

**Create a room (success, returns 201):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms \
  -H "Content-Type: application/json" \
  -d '{"roomName":"general"}'
```

Return:

```json
{ "message": "Chat room 'general' created successfully.", "roomId": "general", "status": "success" }
```

**Create the same room again (duplicate, returns 409):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms \
  -H "Content-Type: application/json" \
  -d '{"roomName":"general"}'
```

Return:

```json
{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "Chat room 'general' already exists" }
```

**Create with a blank name (returns 400):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms \
  -H "Content-Type: application/json" \
  -d '{"roomName":""}'
```

Return:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "roomName: roomName must not be blank" }
```

---

## 2) Join Room — `POST /api/chatapp/chatrooms/{roomId}/join`

**Join an existing room (returns 200):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/join \
  -H "Content-Type: application/json" \
  -d '{"participant":"guest_user"}'
```

Return:

```json
{ "message": "User 'guest_user' joined chat room 'general'.", "status": "success" }
```

**Join a room that does not exist (returns 404):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/ghost/join \
  -H "Content-Type: application/json" \
  -d '{"participant":"guest_user"}'
```

Return:

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Chat room 'ghost' does not exist" }
```

**Join with a blank participant name (returns 400):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/join \
  -H "Content-Type: application/json" \
  -d '{"participant":""}'
```

Return:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "participant: participant must not be blank" }
```

---

## 3) Send Message — `POST /api/chatapp/chatrooms/{roomId}/messages`

**Send a message (returns 200):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/messages \
  -H "Content-Type: application/json" \
  -d '{"participant":"guest_user","message":"Hello, everyone!"}'
```

Return:

```json
{ "message": "Message sent successfully.", "status": "success" }
```

**Send to a room that does not exist (returns 404):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/ghost/messages \
  -H "Content-Type: application/json" \
  -d '{"participant":"guest_user","message":"hi"}'
```

Return:

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Chat room 'ghost' does not exist" }
```

**Send with a blank message (returns 400):**

```bash
curl -X POST http://localhost:8080/api/chatapp/chatrooms/general/messages \
  -H "Content-Type: application/json" \
  -d '{"participant":"guest_user","message":""}'
```

Return:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "message: message must not be blank" }
```

---

## 4) Get Message History — `GET /api/chatapp/chatrooms/{roomId}/messages`

**Get the last 10 messages (returns 200):**

```bash
curl http://localhost:8080/api/chatapp/chatrooms/general/messages
```

Return:

```json
{ "messages": [ { "participant": "guest_user", "message": "Hello, everyone!", "timestamp": "2026-10-13T12:00:00Z" } ] }
```

**Get the last N messages (here 1):**

```bash
curl http://localhost:8080/api/chatapp/chatrooms/general/messages?limit=1
```

Return: last 1 message in the same shape as above.

**History of a room that does not exist (returns 404):**

```bash
curl http://localhost:8080/api/chatapp/chatrooms/ghost/messages
```

Return:

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Chat room 'ghost' does not exist" }
```

**Limit set to 0 (returns 400):**

```bash
curl http://localhost:8080/api/chatapp/chatrooms/general/messages?limit=0
```

Return:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "limit must be a positive integer" }
```

---

## 5) Delete Room — `DELETE /api/chatapp/chatrooms/{roomId}`

**Delete an existing room (returns 200):**

```bash
curl -X DELETE http://localhost:8080/api/chatapp/chatrooms/general
```

Return:

```json
{ "message": "Chat room 'general' deleted successfully.", "status": "success" }
```

**Delete a room that does not exist (returns 404):**

```bash
curl -X DELETE http://localhost:8080/api/chatapp/chatrooms/ghost
```

Return:

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Chat room 'ghost' does not exist" }
```

---

## 6) WebSocket — `ws://localhost:8080/ws/chat`

Install a client once (`npm install -g wscat`), then:

```bash
wscat -c ws://localhost:8080/ws/chat
```

**Join a room:**

```bash
{ "type": "join", "payload": { "roomId": "general", "participant": "alice" } }
```

Return:

```json
{ "type": "joined", "payload": { "roomId": "general", "participant": "alice" } }
```

**Send a chat message (you receive your own echo):**

```bash
{ "type": "chat", "payload": { "participant": "alice", "message": "hi" } }
```

Return:

```json
{ "type": "message", "payload": { "participant": "alice", "message": "hi", "timestamp": "2026-10-13T12:00:00Z" } }
```

> Open a **second** `wscat` in the same room and chat — both terminals receive the same
> `message` frame. A REST message (section 3) also appears in both terminals.

**Chat before joining (error):**

```bash
{ "type": "chat", "payload": { "participant": "rogue", "message": "too soon" } }
```

Return:

```json
{ "type": "error", "payload": { "code": "NOT_IN_ROOM", "message": "You must join a room first" } }
```