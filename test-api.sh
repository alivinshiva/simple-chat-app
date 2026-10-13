#!/usr/bin/env bash
set -u

BASE="${BASE:-http://localhost:8080}"
ROOM="apitest_$$"
TMP=/tmp/chatapp-check.$$
PASS=0
FAIL=0

if ! curl -s -o /dev/null -m 3 "$BASE/api/chatapp/chatrooms/health-probe/messages"; then
  echo "App not reachable at $BASE."
  echo "Start it first:  docker compose up -d   then   mvn spring-boot:run"
  exit 1
fi

curl -s -X DELETE "$BASE/api/chatapp/chatrooms/$ROOM" >/dev/null 2>&1
trap 'curl -s -X DELETE "$BASE/api/chatapp/chatrooms/$ROOM" >/dev/null 2>&1; rm -f "$TMP"' EXIT

req() {
  local method="$1" path="$2" data="${3:-}"
  if [ -n "$data" ]; then
    CODE=$(curl -s -o "$TMP" -w '%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json' -d "$data")
  else
    CODE=$(curl -s -o "$TMP" -w '%{http_code}' -X "$method" "$BASE$path")
  fi
  BODY=$(cat "$TMP")
}

check() {
  local name="$1" want="$2" pat="$3"
  if [ "$CODE" = "$want" ] && printf '%s' "$BODY" | grep -qE "$pat"; then
    echo "PASS  $name"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $name  (got [$CODE] $BODY, expected $want ~/$pat/)"
    FAIL=$((FAIL + 1))
  fi
}

echo "== 1) CREATE ROOM =="
req POST "/api/chatapp/chatrooms" "{\"roomName\":\"$ROOM\"}"
check "create room -> 201" 201 "created successfully"
req POST "/api/chatapp/chatrooms" "{\"roomName\":\"$ROOM\"}"
check "duplicate room -> 409" 409 "already exists"
req POST "/api/chatapp/chatrooms" '{"roomName":""}'
check "blank room name -> 400" 400 "must not be blank"

echo ""
echo "== 2) JOIN ROOM =="
req POST "/api/chatapp/chatrooms/$ROOM/join" '{"participant":"bob"}'
check "join room -> 200" 200 "joined"
req POST "/api/chatapp/chatrooms/ghost-$ROOM/join" '{"participant":"bob"}'
check "join missing room -> 404" 404 "does not exist"
req POST "/api/chatapp/chatrooms/$ROOM/join" '{"participant":""}'
check "blank participant -> 400" 400 "must not be blank"

echo ""
echo "== 3) SEND MESSAGE =="
req POST "/api/chatapp/chatrooms/$ROOM/messages" '{"participant":"bob","message":"Hello, everyone!"}'
check "send message -> 200" 200 "sent successfully"
req POST "/api/chatapp/chatrooms/ghost-$ROOM/messages" '{"participant":"bob","message":"hi"}'
check "send to missing room -> 404" 404 "does not exist"
req POST "/api/chatapp/chatrooms/$ROOM/messages" '{"participant":"bob","message":""}'
check "blank message -> 400" 400 "must not be blank"

echo ""
echo "== 4) MESSAGE HISTORY =="
req GET "/api/chatapp/chatrooms/$ROOM/messages"
check "history -> 200" 200 '"messages"'
req GET "/api/chatapp/chatrooms/$ROOM/messages?limit=1"
check "history limit=1 -> 200" 200 '"messages"'
req GET "/api/chatapp/chatrooms/ghost-$ROOM/messages"
check "history missing room -> 404" 404 "does not exist"
req GET "/api/chatapp/chatrooms/$ROOM/messages?limit=0"
check "limit=0 -> 400" 400 "positive integer"

echo ""
echo "== 5) DELETE ROOM =="
req DELETE "/api/chatapp/chatrooms/ghost-$ROOM"
check "delete missing room -> 404" 404 "does not exist"
req DELETE "/api/chatapp/chatrooms/$ROOM"
check "delete room -> 200" 200 "deleted successfully"
req POST "/api/chatapp/chatrooms" "{\"roomName\":\"$ROOM\"}"
check "recreate after delete -> 201" 201 "created successfully"

echo ""
echo "== 6) WEB SOCKET (Node.js >= 22 required) =="
if [ "$(node -p 'typeof WebSocket' 2>/dev/null || echo unavailable)" != "function" ]; then
  echo "SKIP  WS tests - no global WebSocket in node $(node -v 2>/dev/null)"
else
  ROOM_NAME="$ROOM" node <<'EOF'
const ROOM = process.env.ROOM_NAME;
const url = "ws://localhost:8080/ws/chat";
function client() {
  const ws = new WebSocket(url);
  const inbox = [];
  ws.onmessage = e => inbox.push(JSON.parse(e.data));
  return { ws, inbox, send: (t, p) => ws.send(JSON.stringify({ type: t, payload: p })),
    wait: (t, ms = 4000) => new Promise((res, rej) => {
      const s = Date.now();
      const iv = setInterval(() => {
        const i = inbox.findIndex(f => f.type === t);
        if (i >= 0) { clearInterval(iv); res(inbox.splice(i, 1)[0]); }
        else if (Date.now() - s > ms) { clearInterval(iv); rej(new Error("timeout waiting for " + t)); }
      }, 25);
    }) };
}
(async () => {
  const out = [];
  const rogue = client();
  await new Promise(r => rogue.ws.onopen = r);
  rogue.send("chat", { participant: "rogue", message: "too soon" });
  const err = await rogue.wait("error");
  out.push(["chat before join -> NOT_IN_ROOM", err.payload.code === "NOT_IN_ROOM"]);
  rogue.ws.close();

  const a = client(), b = client();
  await Promise.all([new Promise(r => a.ws.onopen = r), new Promise(r => b.ws.onopen = r)]);
  a.send("join", { roomId: ROOM, participant: "bob" });
  await a.wait("joined");
  b.send("join", { roomId: ROOM, participant: "carol" });
  await b.wait("joined");
  a.send("chat", { participant: "bob", message: "ping" });
  const echo = await a.wait("message");
  const fan = await b.wait("message");
  out.push(["sender echo", echo.payload.message === "ping"]);
  out.push(["fan-out to other client", fan.payload.message === "ping"]);
  a.ws.close(); b.ws.close();

  let fails = 0;
  for (const [n, ok] of out) {
    console.log((ok ? "PASS" : "FAIL") + "  WS " + n);
    if (!ok) fails++;
  }
  process.exit(fails ? 1 : 0);
})().catch(e => { console.error("FAIL  WS " + e.message); process.exit(1); });
EOF
  WS_EXIT=$?
  [ "$WS_EXIT" -eq 0 ] || FAIL=$((FAIL + 1))
fi

echo ""
echo "=========================================="
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]