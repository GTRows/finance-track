#!/bin/bash
# ============================================================
# FinTrack Pro -- SSL Certificate Issuance (Let's Encrypt)
#
# Self-hosted (no VPS) usage: your DOMAIN A/AAAA records must
# point to this host's public IP, and ports 80/443 must be
# reachable from the internet.
#
# Usage: ./scripts/ssl-setup.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [ -f "${PROJECT_DIR}/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "${PROJECT_DIR}/.env"
    set +a
fi

DOMAIN="${DOMAIN:-fatihaciroglu.dev}"
SSL_EMAIL="${SSL_EMAIL:-aciroglu.fatih@gmail.com}"
STAGING="${STAGING:-0}"

if [ "$DOMAIN" = "localhost" ] || [ -z "$DOMAIN" ]; then
    echo "ERROR: DOMAIN must be a real public hostname (got '$DOMAIN')."
    exit 1
fi
if [ -z "$SSL_EMAIL" ]; then
    echo "ERROR: SSL_EMAIL is required for Let's Encrypt registration."
    exit 1
fi

cd "$PROJECT_DIR"

echo "==> Ensuring nginx is running so the ACME HTTP-01 challenge can be served"
docker compose up -d nginx

STAGING_FLAG=""
if [ "$STAGING" = "1" ]; then
    STAGING_FLAG="--staging"
    echo "==> STAGING mode enabled (certs will not be trusted)"
fi

echo "==> Issuing certificate for ${DOMAIN} (and www.${DOMAIN})"
docker compose run --rm --entrypoint "" certbot \
    certbot certonly \
        --webroot -w /var/www/certbot \
        -d "${DOMAIN}" -d "www.${DOMAIN}" \
        --email "${SSL_EMAIL}" \
        --agree-tos --no-eff-email \
        --non-interactive \
        --keep-until-expiring \
        ${STAGING_FLAG}

echo "==> Enabling the TLS server block (nginx/conf.d/tls.conf)"
cp "${PROJECT_DIR}/nginx/tls.conf.template" "${PROJECT_DIR}/nginx/conf.d/tls.conf"

echo "==> Reloading nginx to pick up the new certificate"
docker compose exec nginx nginx -s reload || docker compose restart nginx

echo ""
echo "Done. Visit https://${DOMAIN}/ to verify."
echo "Renewals run automatically in the 'certbot' service (every 12h)."
