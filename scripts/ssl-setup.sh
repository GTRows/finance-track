#!/bin/bash
# ============================================================
# FinTrack Pro -- SSL Certificate Setup (Let's Encrypt)
# Prerequisites: domain DNS A record pointing to this server
# Usage: ./scripts/ssl-setup.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [ -f "${PROJECT_DIR}/.env" ]; then
    set -a
    source "${PROJECT_DIR}/.env"
    set +a
fi

DOMAIN="${DOMAIN:-}"
SSL_EMAIL="${SSL_EMAIL:-}"

if [ -z "$DOMAIN" ] || [ "$DOMAIN" = "localhost" ]; then
    echo "ERROR: Set DOMAIN in .env to your domain name (not localhost)"
    exit 1
fi

if [ -z "$SSL_EMAIL" ]; then
    echo "ERROR: Set SSL_EMAIL in .env for Let's Encrypt registration"
    exit 1
fi

echo "Installing certbot..."
apt-get update -qq && apt-get install -y -qq certbot

echo "Obtaining certificate for ${DOMAIN}..."
certbot certonly --standalone \
    -d "$DOMAIN" \
    --email "$SSL_EMAIL" \
    --agree-tos \
    --non-interactive

echo "Certificate obtained successfully."
echo ""
echo "Next steps:"
echo "1. Update nginx/nginx.conf: uncomment the HTTPS server block"
echo "2. Replace YOUR_DOMAIN with ${DOMAIN}"
echo "3. Restart: docker compose restart nginx"
echo ""
echo "Auto-renewal is configured via certbot systemd timer."
certbot renew --dry-run
