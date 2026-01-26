#!/usr/bin/env bash
set -euo pipefail

# ============================================
# Config (환경에 맞게 여기만 수정)
# ============================================
BASE_URL="${BASE_URL:-http://logcollector-app:8081}"  # docker network 내부에서 app 서비스명 사용

# KB DB 접속정보 (docker run에서 -e 로 주입)
MYSQL_HOST="${MYSQL_HOST:-logcollector-db}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-kb_root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-knowledge_base}"

KB_DB_USER="${KB_DB_USER:-root}"
KB_DB_PASS="${KB_DB_PASS:-root}"

# Scheduler close 지연이 길면 테스트가 오래 걸립니다.
# 로컬 검증용으로 close.delay.minutes를 1~2로 설정하는 것을 권장
SLEEP_SEC="${SLEEP_SEC:-130}"

SERVICE_NAME="${SERVICE_NAME:-TESTE-API}"
MESSAGE="${MESSAGE:-[VERIFY_S1] RedisCommandTimeoutException: Command timed out after 500ms.}"
STACKTRACE="${STACKTRACE:-}"
LOG_LEVEL="${LOG_LEVEL:-ERROR}"

# 비동기 반영 대기(폴링)
WAIT_POLL_SEC="${WAIT_POLL_SEC:-1}"
WAIT_MAX_TRIES="${WAIT_MAX_TRIES:-30}"

# ============================================
# Helpers
# ============================================
log() { printf "\n[%s] %s\n" "$(date '+%F %T')" "$*"; }

curl_json() {
  curl -sS --fail -H "Content-Type: application/json" "$@"
}

kb_mysql() {
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -e "$1"
}


calc_log_hash() {
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
    return
  fi
  echo "$resp" | sed -n 's/.*"logHash"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

latest_log_hash_from_db() {
  kb_mysql "SELECT log_hash FROM incident WHERE service_name='${SERVICE_NAME}' ORDER BY updated_at DESC LIMIT 1;"
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

# ---- [추가] 비동기 반영 대기 유틸 ----
wait_sql_nonempty() {
  local q="$1"
  local label="$2"
  local i=0
  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    local v
    v="$(kb_mysql "$q" 2>/dev/null || true)"
    if [[ -n "${v// }" ]]; then
      echo "$v"
      return 0
    fi
    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done
  echo ""
}

wait_incident_status_eq() {
  local hash="$1"
  local expected="$2"
  local label="$3"
  local i=0
  local st=""
  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    st="$(kb_mysql "SELECT status FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
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

wait_repeat_ge() {
  local hash="$1"
  local min="$2"
  local label="$3"
  local i=0
  local rc=""
  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    rc="$(kb_mysql "SELECT repeat_count FROM incident WHERE log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    if [[ -n "${rc// }" && "$rc" -ge "$min" ]]; then
      echo "$rc"
      return 0
    fi
    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done
  echo "ASSERT FAIL: $label (expected >= $min, actual=$rc)" >&2
  exit 1
}

wait_kb_article_exists() {
  local hash="$1"
  local label="$2"
  local i=0
  local st=""
  while [[ $i -lt $WAIT_MAX_TRIES ]]; do
    st="$(kb_mysql "SELECT a.status FROM kb_article a JOIN incident i ON a.incident_id=i.id WHERE i.log_hash='${hash}' LIMIT 1;" 2>/dev/null || true)"
    if [[ -n "${st// }" ]]; then
      echo "$st"
      return 0
    fi
    sleep "$WAIT_POLL_SEC"
    i=$((i+1))
  done
  echo "ASSERT FAIL: $label (kb_article not found for log_hash=$hash)" >&2
  exit 1
}
# ---- [추가 끝] ----

# ============================================
# Scenario 1: NEW -> repeat -> RESOLVED -> CLOSED
# ============================================
log "1) Send log (create OPEN incident)"
curl_json -X POST "$BASE_URL/api/logs" -d "{
  \"serviceName\": \"$SERVICE_NAME\",
  \"logLevel\": \"$LOG_LEVEL\",
  \"message\": \"$MESSAGE\",
  \"stackTrace\": \"$STACKTRACE\",
  \"hostName\": \"host-1\"
}" >/dev/null

LOG_HASH="$(calc_log_hash)"
if [[ -z "$LOG_HASH" ]]; then
  LOG_HASH="$(latest_log_hash_from_db)"
fi
assert_not_empty "$LOG_HASH" "logHash"
log "logHash=$LOG_HASH"

log "Verify incident is OPEN, repeat_count=1 (wait)"
wait_incident_status_eq "$LOG_HASH" "OPEN" "incident.status after first log"
RC1="$(wait_repeat_ge "$LOG_HASH" 1 "incident.repeat_count after first log")"
assert_equals "1" "$RC1" "incident.repeat_count after first log"

log "2) Send same log again (repeat)"
curl_json -X POST "$BASE_URL/api/logs" -d "{
  \"serviceName\": \"$SERVICE_NAME\",
  \"logLevel\": \"$LOG_LEVEL\",
  \"message\": \"$MESSAGE\",
  \"stackTrace\": \"$STACKTRACE\",
  \"hostName\": \"host-2\"
}" >/dev/null

RC2="$(wait_repeat_ge "$LOG_HASH" 2 "repeat_count after second log")"
log "repeat_count=$RC2 OK"

log "3) Mark RESOLVED"
curl -sS --fail -X PATCH "$BASE_URL/api/incidents/${LOG_HASH}/status?newStatus=RESOLVED" >/dev/null

wait_incident_status_eq "$LOG_HASH" "RESOLVED" "incident.status after RESOLVED"
RESOLVED_AT="$(wait_sql_nonempty "SELECT IFNULL(resolved_at,'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" "incident.resolved_at")"
CLOSE_ELIGIBLE="$(wait_sql_nonempty "SELECT IFNULL(close_eligible_at,'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" "incident.close_eligible_at")"
log "resolved_at=$RESOLVED_AT, close_eligible_at=$CLOSE_ELIGIBLE"

log "Verify kb_article exists for incident (wait)"
KB_STATUS="$(wait_kb_article_exists "$LOG_HASH" "kb_article exists")"
log "kb_article.status=$KB_STATUS"

log "4) Force close_eligible_at to past (test only)"
kb_mysql "UPDATE incident SET close_eligible_at = DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE log_hash='${LOG_HASH}';"
FORCED_ELIGIBLE="$(wait_sql_nonempty "SELECT IFNULL(close_eligible_at,'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" "incident.close_eligible_at(after force)")"
log "close_eligible_at(forced)=$FORCED_ELIGIBLE"

log "5) Trigger scheduler manually"
curl -sS --fail -X POST "$BASE_URL/api/test/scheduler/run" >/dev/null

log "6) Verify CLOSED"
wait_incident_status_eq "$LOG_HASH" "CLOSED" "incident.status expected CLOSED after manual trigger"
CLOSED_AT="$(wait_sql_nonempty "SELECT IFNULL(closed_at,'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;" "incident.closed_at")"
log "OK: CLOSED at $CLOSED_AT"

log "DONE"

$PROJECT_ROOT = (Resolve-Path "..\..").Path

# docker run --rm -it `
#  --network compose_default `
#  -e BASE_URL="http://app:8081" `
#  -e MYSQL_HOST="logcollector-db" `
#  -e MYSQL_PORT="3306" `
#  -e MYSQL_USER="kb_root" `
#  -e MYSQL_PASS="root" `
#  -e MYSQL_DB="knowledge_base" `
#  -e SLEEP_SEC="1" `
#  -v "${PROJECT_ROOT}:/work" `
#  -w /work `
#  alpine:3.20 sh -lc `
#  "apk add --no-cache curl mariadb-client bash >/dev/null && chmod +x docs/scripts/1_verify_scenario.docker.sh && docs/scripts/1_verify_scenario.docker.sh"

