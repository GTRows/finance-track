#!/bin/bash
# ============================================================
# FinTrack Pro -- First-Time Setup
# Usage: ./scripts/setup.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== FinTrack Pro Setup ==="

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed. Install Docker Desktop first."
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running. Start Docker Desktop first."
    exit 1
fi

# Create .env if missing
if [ ! -f "${PROJECT_DIR}/.env" ]; then
    echo "Creating .env from .env.example..."
    cp "${PROJECT_DIR}/.env.example" "${PROJECT_DIR}/.env"

    # Generate random secrets
    JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
    PG_PASS=$(openssl rand -base64 24 | tr -d '\n')
    REDIS_PASS=$(openssl rand -base64 24 | tr -d '\n')

    if [[ "$OSTYPE" == "darwin"* ]]; then
        SED_INPLACE="sed -i ''"
    else
        SED_INPLACE="sed -i"
    fi

    $SED_INPLACE "s|CHANGE_ME_generate_a_secure_256_bit_secret_key_here|${JWT_SECRET}|" "${PROJECT_DIR}/.env"
    $SED_INPLACE "s|CHANGE_ME_strong_password_here|${PG_PASS}|" "${PROJECT_DIR}/.env"
    $SED_INPLACE "s|CHANGE_ME_redis_password|${REDIS_PASS}|" "${PROJECT_DIR}/.env"

    echo ".env created with random secrets."
else
    echo ".env already exists, skipping."
fi

# Create directories
mkdir -p "${PROJECT_DIR}/backups"
mkdir -p "${PROJECT_DIR}/logs"

# Build and start
echo "Building and starting services..."
cd "$PROJECT_DIR"
docker compose up -d --build

echo ""
echo "Waiting for services to start..."
sleep 10

# Health check
echo "Checking health..."
if curl -sf http://localhost/api/v1/health > /dev/null 2>&1; then
    echo "Backend is healthy."
else
    echo "Backend is still starting. Check: docker compose logs -f backend"
fi

echo ""
echo "=== Setup Complete ==="
echo "Open http://localhost in your browser."
echo "Register your first account to get started."
