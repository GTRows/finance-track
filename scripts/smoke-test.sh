#!/bin/bash
# ============================================================
# FinTrack Pro -- Automated Smoke Test
#
# Starts the full Docker stack, waits for health, then exercises
# the critical end-to-end path: register -> login -> create
# portfolio -> add holding -> read dashboard. Fails loudly on
# any non-2xx response.
#
# Usage:
#   ./scripts/smoke-test.sh [BASE_URL]
#
# BASE_URL defaults to http://localhost (via nginx on port 80).
# Override to hit the container stack directly, e.g.:
#   ./scripts/smoke-test.sh http://localhost:8080
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost}"
API="${BASE_URL%/}/api/v1"

TS="$(date +%s)"
USERNAME="smoke_${TS}"
EMAIL="smoke_${TS}@fintrack.test"
PASSWORD="SmokeTest!${TS}"

pass() { printf '  [ OK ] %s\n' "$1"; }
fail() { printf '  [FAIL] %s\n' "$1" >&2; exit 1; }
info() { printf '\n==> %s\n' "$1"; }

require() {
    command -v "$1" >/dev/null 2>&1 || fail "missing required tool: $1"
}
require curl
require jq

call() {
    # call METHOD PATH [JSON_BODY] [AUTH_TOKEN]
    local method="$1" path="$2" body="${3:-}" token="${4:-}"
    local tmp status
    tmp="$(mktemp)"
    local args=(-sS -o "$tmp" -w '%{http_code}' -X "$method" "${API}${path}")
    args+=(-H "Content-Type: application/json")
    args+=(-H "Accept-Language: en")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer ${token}")
    [ -n "$body" ]  && args+=(--data "$body")
    status="$(curl "${args[@]}")"
    RESPONSE_BODY="$(cat "$tmp")"
    rm -f "$tmp"
    RESPONSE_STATUS="$status"
}

expect_2xx() {
    if [[ "$RESPONSE_STATUS" =~ ^2 ]]; then
        pass "$1 -> $RESPONSE_STATUS"
    else
        echo "$RESPONSE_BODY" >&2
        fail "$1 -> $RESPONSE_STATUS"
    fi
}

info "Target: ${BASE_URL}"

info "Waiting for backend /health (up to 90s)"
for i in $(seq 1 45); do
    if curl -sf "${API}/health" >/dev/null 2>&1; then
        pass "backend is healthy after ${i} checks"
        break
    fi
    if [ "$i" = 45 ]; then
        fail "backend did not become healthy in time"
    fi
    sleep 2
done

info "Register a fresh user (${USERNAME})"
call POST /auth/register "$(jq -nc --arg u "$USERNAME" --arg e "$EMAIL" --arg p "$PASSWORD" \
    '{username:$u,email:$e,password:$p}')"
expect_2xx "POST /auth/register"
ACCESS="$(echo "$RESPONSE_BODY" | jq -r '.accessToken')"
REFRESH="$(echo "$RESPONSE_BODY" | jq -r '.refreshToken')"
[ "$ACCESS" != "null" ] || fail "no accessToken in register response"

info "GET /auth/me"
call GET /auth/me "" "$ACCESS"
expect_2xx "GET /auth/me"

info "GET /settings (should auto-provision defaults)"
call GET /settings "" "$ACCESS"
expect_2xx "GET /settings"

info "PUT /settings (change currency to USD)"
call PUT /settings '{"currency":"USD","language":"en","theme":"dark","timezone":"Europe/Istanbul"}' "$ACCESS"
expect_2xx "PUT /settings"
CUR="$(echo "$RESPONSE_BODY" | jq -r '.currency')"
[ "$CUR" = "USD" ] || fail "currency did not update (got '$CUR')"

info "POST /portfolios"
call POST /portfolios '{"name":"Smoke Portfolio","type":"INDIVIDUAL"}' "$ACCESS"
expect_2xx "POST /portfolios"
PORTFOLIO_ID="$(echo "$RESPONSE_BODY" | jq -r '.id')"
[ "$PORTFOLIO_ID" != "null" ] || fail "portfolio id missing"

info "GET /assets?type=CRYPTO"
call GET "/assets?type=CRYPTO" "" "$ACCESS"
expect_2xx "GET /assets"
ASSET_ID="$(echo "$RESPONSE_BODY" | jq -r '.[0].id')"
[ "$ASSET_ID" != "null" ] || fail "no crypto assets seeded"

info "POST /portfolios/{id}/holdings"
call POST "/portfolios/${PORTFOLIO_ID}/holdings" \
    "$(jq -nc --arg a "$ASSET_ID" '{assetId:$a, quantity:0.05, averageCost:1000000}')" \
    "$ACCESS"
expect_2xx "POST holding"

info "GET /dashboard"
call GET /dashboard "" "$ACCESS"
expect_2xx "GET /dashboard"

info "POST /auth/logout"
call POST /auth/logout "$(jq -nc --arg r "$REFRESH" '{refreshToken:$r}')" "$ACCESS"
expect_2xx "POST /auth/logout"

echo ""
echo "All smoke-test checks passed."
