#!/usr/bin/env bash
set -euo pipefail

# ============================================
# Scenario 2 (Docker-run friendly):
#   OPEN -> RESOLVED -> new occurrence -> REOPEN(OPEN)
#
# Requirements:
#   - This script runs inside a docker run container (alpine) and connects to:
#     - App: BASE_URL (e.g. http://app:8081)
#     - KB DB: MYSQL_HOST/PORT/USER/PASS/DB (e.g. logcollector-db:3306)
# ============================================

BASE_URL="${BASE_URL:-http://app:8081}"

# KB DB connection (docker network)
MYSQL_HOST="${MYSQL_HOST:-logcollector-db}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-kb_root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-knowledge_base}"

SERVICE_NAME="${SERVICE_NAME:-TESTE-API}"
MESSAGE="${MESSAGE:-[VERIFY_S2] RedisCommandTimeoutException: Command timed out after 500ms.}"
STACKTRACE="${STACKTRACE:-}"
LOG_LEVEL="${LOG_LEVEL:-ERROR}"

# Polling
WAIT_POLL_SEC="${WAIT_POLL_SEC:-0.2}"
WAIT_MAX_TRIES="${WAIT_MAX_TRIES:-100}"   # 100 * 0.2s = 20s

log() { printf "\n[%s] %s\n" "$(date '+%F %T')" "$*"; }

curl_json() {
  curl -sS --fail -H "Content-Type: application/json" "$@"
}

kb_mysql() {
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -e "$1"
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    echo "ASSERT FAIL: $label (expected=$expected, actual=$actual)" >&2
    exit 1
  fi
}

assert_not_empty() {
  local value="$1"
  local label="$2"
  if [[ -z "${value// }" ]]; then
    echo "ASSERT FAIL: $label is empty" >&2
    exit 1
  fi
}

# ---- Wait helpers ----

wait_incident_row_exists() {
  local hash="$1"
  local label="$2"
  local i=0
  local c="0"

  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    c="$(kb_mysql "SELECT COUNT(*) FROM incident WHERE log_hash='${hash}';" 2>/dev/null || echo 0)"
    if [[ -n "${c// }" && "$c" != "0" ]]; then
      return 0
    fi
    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done

  echo "ASSERT FAIL: $label (incident row not found) log_hash=$hash" >&2
  exit 1
}

wait_incident_status_eq() {
  local hash="$1"
  local expected="$2"
  local label="$3"
  local i=0
  local st=""

  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    st="$(kb_mysql "SELECT IFNULL(status,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    if [[ -n "${st// }" && "$st" == "$expected" ]]; then
      echo "$st"
      return 0
    fi
    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done

  echo "ASSERT FAIL: $label (expected=$expected, actual=$st)" >&2
  exit 1
}

wait_reopen_effects() {
  local hash="$1"
  local i=0
  local st="" reopened="" resolved="" closed="" eligible=""

  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    st="$(kb_mysql "SELECT IFNULL(status,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    reopened="$(kb_mysql "SELECT IFNULL(reopened_at,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    resolved="$(kb_mysql "SELECT IFNULL(resolved_at,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    closed="$(kb_mysql "SELECT IFNULL(closed_at,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    eligible="$(kb_mysql "SELECT IFNULL(close_eligible_at,'') FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"

    # Reopen policy you described:
    # - status becomes OPEN
    # - reopened_at is set
    # - resolved_at/closed_at/close_eligible_at cleared
    if [[ "$st" == "OPEN" && -n "${reopened// }" && -z "${resolved// }" && -z "${closed// }" && -z "${eligible// }" ]]; then
      echo "$st|$reopened|$resolved|$closed|$eligible"
      return 0
    fi

    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done

  echo "ASSERT FAIL: reopen effects not observed (status=$st reopened_at=$reopened resolved_at=$resolved closed_at=$closed close_eligible_at=$eligible)" >&2
  exit 1
}

# ---- Hash helper ----

calc_log_hash() {
  # /api/test/hash should return {"logHash":"..."} (or similar)
  set +e
  local resp
  resp="$(curl_json -X POST "$BASE_URL/api/test/hash" -d "{
    \"serviceName\": \"$SERVICE_NAME\",
    \"message\": \"$MESSAGE\",
    \"stackTrace\": \"$STACKTRACE\"
  }" 2>/dev/null)"
  local code=$?
  set -e

  if [[ $code -ne 0 ]]; then
    echo ""
    return 0
  fi

  echo "$resp" | sed -n 's/.*"logHash"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

# ---- Status change with retry (race fix) ----

patch_resolved_with_retry() {
  local hash="$1"
  local maxTries="${2:-100}"     # maxTries * WAIT_POLL_SEC
  local i code body

  for i in $(seq 1 "$maxTries"); do
    code="$(curl -sS -o /tmp/resolve_body.txt -w "%{http_code}" \
      -X PATCH "$BASE_URL/api/incidents/${hash}/status?newStatus=RESOLVED" || true)"
    body="$(cat /tmp/resolve_body.txt 2>/dev/null || true)"

    # success
    if [[ "$code" == "200" || "$code" == "204" ]]; then
      return 0
    fi

    # race: incident not found yet (400 with specific message)
    if [[ "$code" == "400" && "$body" == *"찾을 수 없습니다"* ]]; then
      sleep "$WAIT_POLL_SEC"
      continue
    fi

    echo "RESOLVED PATCH failed (http_code=$code) body=$body" >&2
    return 1
  done

  echo "RESOLVED PATCH retry exhausted (last_body=$body)" >&2
  return 1
}

# ============================================
# Scenario 2
# ============================================

log "1) Send log (create OPEN incident)"
curl_json -X POST "$BASE_URL/api/logs" -d "{
  \"serviceName\": \"$SERVICE_NAME\",
  \"logLevel\": \"$LOG_LEVEL\",
  \"message\": \"$MESSAGE\",
  \"stackTrace\": \"$STACKTRACE\",
  \"hostName\": \"host-a\"
}" >/dev/null

LOG_HASH="$(calc_log_hash)"
assert_not_empty "$LOG_HASH" "logHash(calc_log_hash)"
log "logHash=$LOG_HASH"

# Debug: ensure DB connection targets what you expect
log "DEBUG mysql whoami/db"
kb_mysql "SELECT DATABASE(), USER(), @@hostname;" || true

# Wait until incident row exists and becomes OPEN (async pipeline)
wait_incident_row_exists "$LOG_HASH" "incident row exists"
wait_incident_status_eq "$LOG_HASH" "OPEN" "incident.status before RESOLVED"

log "DEBUG incident row"
kb_mysql "SELECT id,status,repeat_count,created_at,last_occurred_at FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" || true

log "2) Mark RESOLVED (with retry for async race)"
patch_resolved_with_retry "$LOG_HASH" 100

wait_incident_status_eq "$LOG_HASH" "RESOLVED" "incident.status after RESOLVED"

log "DB dump after RESOLVED"
kb_mysql "SELECT id,status,IFNULL(resolved_at,''),IFNULL(reopened_at,''),IFNULL(closed_at,''),IFNULL(close_eligible_at,'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" || true

log "3) Send same log again (should reopen to OPEN)"
curl_json -X POST "$BASE_URL/api/logs" -d "{
  \"serviceName\": \"$SERVICE_NAME\",
  \"logLevel\": \"$LOG_LEVEL\",
  \"message\": \"$MESSAGE\",
  \"stackTrace\": \"$STACKTRACE\",
  \"hostName\": \"host-b\"
}" >/dev/null

out="$(wait_reopen_effects "$LOG_HASH")"
STATUS_O="${out%%|*}"
rest="${out#*|}"
REOPENED_AT="${rest%%|*}"

assert_equals "OPEN" "$STATUS_O" "incident.status after recurrence"
assert_not_empty "$REOPENED_AT" "incident.reopened_at"

log "OK: reopened_at=$REOPENED_AT, status=$STATUS_O"
log "DONE"

#  docker run --rm -it `
  #>>   --network compose_default `
  #>>   -e BASE_URL="http://app:8081" `
  #>>   -e MYSQL_HOST="logcollector-db" `
  #>>   -e MYSQL_PORT="3306" `
  #>>   -e MYSQL_USER="kb_root" `
  #>>   -e MYSQL_PASS="root" `
  #>>   -e MYSQL_DB="knowledge_base" `
  #>>   -e SERVICE_NAME="TESTE-API-S2-$RID" `
  #>>   -e MESSAGE="[VERIFY_S2] RedisCommandTimeoutException: Command timed out after 500ms. rid=$RID" `
  #>>   -v "${PROJECT_ROOT}:/work" `
  #>>   -w /work `
  #>>   alpine:3.20 sh -lc `
  #>>   "apk add --no-cache curl mariadb-client bash >/dev/null && chmod +x docs/scripts/2_verify_scenario.docker.sh && docs/scripts/2_verify_scenario.docker.sh"