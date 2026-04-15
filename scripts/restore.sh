#!/bin/bash
# ============================================================
# FinTrack Pro -- Database Restore
# Restores a gzipped pg_dump file into the running postgres
# container. The current database contents are dropped first.
#
# Usage: ./scripts/restore.sh backups/fintrack_YYYYMMDD_HHMMSS.sql.gz
# ============================================================

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <backup-file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: backup file not found: $BACKUP_FILE"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [ -f "${PROJECT_DIR}/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "${PROJECT_DIR}/.env"
    set +a
fi

POSTGRES_USER="${POSTGRES_USER:-fintrack}"
POSTGRES_DB="${POSTGRES_DB:-fintrack}"

echo "WARNING: this will drop and recreate database '${POSTGRES_DB}'."
read -p "Type 'yes' to continue: " confirm
if [ "$confirm" != "yes" ]; then
    echo "Aborted."
    exit 1
fi

cd "$PROJECT_DIR"

echo "Stopping backend so it releases connections..."
docker compose stop backend

echo "Dropping and recreating database..."
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c \
    "DROP DATABASE IF EXISTS ${POSTGRES_DB};"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c \
    "CREATE DATABASE ${POSTGRES_DB};"

echo "Restoring from ${BACKUP_FILE}..."
gunzip -c "$BACKUP_FILE" | docker compose exec -T postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

echo "Starting backend again..."
docker compose start backend

echo "Restore complete."
