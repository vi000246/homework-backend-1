#!/usr/bin/env bash
# End-to-end smoke test for the notification service.
# Requires the app running on $BASE (default http://localhost:8080) with the docker-compose stack up.
# Usage: ./scripts/smoke-test.sh
set -u

BASE="${BASE:-http://localhost:8080}"
pass=0; fail=0

check() { # check <name> <expected> <actual>
  if [ "$2" = "$3" ]; then echo "  ✅ $1 (HTTP $3)"; pass=$((pass+1));
  else echo "  ❌ $1 — expected $2, got $3"; fail=$((fail+1)); fi
}

echo "== Notification Service smoke test ($BASE) =="

echo "[1] create email (expect 201)"
resp=$(curl -s -w '\n%{http_code}' -XPOST "$BASE/notifications" -H 'Content-Type: application/json' \
  -d '{"type":"email","recipient":"user@example.com","subject":"Welcome!","content":"Thanks!"}')
code=$(echo "$resp" | tail -1); bodyline=$(echo "$resp" | sed '$d')
check "create" 201 "$code"
ID=$(echo "$bodyline" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
echo "      created id=$ID"

echo "[2] get by id (expect 200)"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/notifications/$ID"); check "get-by-id" 200 "$code"

echo "[3] recent (expect 200)"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/notifications/recent"); check "recent" 200 "$code"

echo "[4] create sms (expect 201)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$BASE/notifications" -H 'Content-Type: application/json' \
  -d '{"type":"sms","recipient":"+15551234567","subject":"Hi","content":"yo"}'); check "create-sms" 201 "$code"

echo "[5] update (expect 200)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XPUT "$BASE/notifications/$ID" -H 'Content-Type: application/json' \
  -d '{"subject":"Updated","content":"Updated body"}'); check "update" 200 "$code"

echo "[6] delete (expect 204)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XDELETE "$BASE/notifications/$ID"); check "delete" 204 "$code"

echo "[7] get deleted (expect 404)"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/notifications/$ID"); check "get-deleted" 404 "$code"

echo "[8] invalid type (expect 400)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$BASE/notifications" -H 'Content-Type: application/json' \
  -d '{"type":"fax","recipient":"user@example.com"}'); check "invalid-type" 400 "$code"

echo "[9] email type with phone recipient (expect 400)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$BASE/notifications" -H 'Content-Type: application/json' \
  -d '{"type":"email","recipient":"+15551234567"}'); check "wrong-recipient-format" 400 "$code"

echo "[10] update missing (expect 404)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XPUT "$BASE/notifications/999999" -H 'Content-Type: application/json' \
  -d '{"subject":"x","content":"y"}'); check "update-missing" 404 "$code"

echo "[11] delete missing (expect 404)"
code=$(curl -s -o /dev/null -w '%{http_code}' -XDELETE "$BASE/notifications/999999"); check "delete-missing" 404 "$code"

echo "== result: $pass passed, $fail failed =="
[ "$fail" -eq 0 ]
