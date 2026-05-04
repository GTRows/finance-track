#!/usr/bin/env bash
# Regenerates frontend/openapi.json by booting the backend test slice and dumping
# /v3/api-docs. The OpenApiSpecGeneratorTest is opt-in via -Dopenapi.generate=true,
# so day-to-day `mvn verify` is not affected.
#
# An ephemeral Postgres container is started directly via the docker CLI to avoid
# Testcontainers' Docker auto-detection (unreliable on Windows + Docker Desktop's
# user-mode pipe). The container is removed on exit.
#
# Usage: bash scripts/regen-openapi.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

CONTAINER_NAME="fintrack-openapi-pg"
HOST_PORT="${OPENAPI_PG_PORT:-15432}"

cleanup() {
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

echo ">> Starting ephemeral Postgres on localhost:${HOST_PORT}"
docker run -d --rm \
    --name "${CONTAINER_NAME}" \
    -e POSTGRES_DB=fintrack \
    -e POSTGRES_USER=fintrack \
    -e POSTGRES_PASSWORD=fintrack \
    -p "${HOST_PORT}:5432" \
    postgres:16-alpine >/dev/null

echo ">> Waiting for Postgres to accept connections"
for i in $(seq 1 60); do
    if docker exec "${CONTAINER_NAME}" pg_isready -U fintrack -d fintrack >/dev/null 2>&1; then
        echo "   ready after ${i}s"
        break
    fi
    if [ "${i}" -eq 60 ]; then
        echo "Postgres did not become ready within 60s" >&2
        exit 1
    fi
    sleep 1
done

cd "${REPO_ROOT}/backend"
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${HOST_PORT}/fintrack" \
SPRING_DATASOURCE_USERNAME=fintrack \
SPRING_DATASOURCE_PASSWORD=fintrack \
./mvnw -B -ntp \
    -Dopenapi.generate=true \
    -Dtest=OpenApiSpecGeneratorTest \
    -DfailIfNoTests=false \
    test

echo ">> Wrote ${REPO_ROOT}/frontend/openapi.json"
