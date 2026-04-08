#!/bin/bash
# ============================================================
# FinTrack Pro -- Database Backup
# Creates a compressed PostgreSQL dump. Keeps last 30 days.
# Usage: ./scripts/backup.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${PROJECT_DIR}/backups"
DATE=$(date +%Y%m%d_%H%M%S)
FILENAME="fintrack_${DATE}.sql.gz"

# Load environment variables
if [ -f "${PROJECT_DIR}/.env" ]; then
    set -a
    source "${PROJECT_DIR}/.env"
    set +a
fi

POSTGRES_USER="${POSTGRES_USER:-fintrack}"
POSTGRES_DB="${POSTGRES_DB:-fintrack}"

mkdir -p "$BACKUP_DIR"

echo "Starting backup..."
docker compose -f "${PROJECT_DIR}/docker-compose.yml" exec -T postgres \
    pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "${BACKUP_DIR}/${FILENAME}"

echo "Backup saved: ${BACKUP_DIR}/${FILENAME}"

# Clean up old backups (keep last 30 days)
find "$BACKUP_DIR" -name "fintrack_*.sql.gz" -mtime +30 -delete 2>/dev/null || true
echo "Cleanup complete (removed backups older than 30 days)"
