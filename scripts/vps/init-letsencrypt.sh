#!/usr/bin/env bash
set -euo pipefail

# Initialize Let's Encrypt certificates for docker compose profile "https".
# Run this on the VPS, from the repo root: /opt/smartparking

DOMAIN="smartparking.id.vn"
DOMAIN_WWW="www.smartparking.id.vn"
EMAIL="${LETSENCRYPT_EMAIL:-}"

if [[ -z "$EMAIL" ]]; then
  echo "ERROR: Set LETSENCRYPT_EMAIL env var (e.g., export LETSENCRYPT_EMAIL=admin@smartparking.id.vn)" >&2
  exit 1
fi

# First issuance: use standalone mode (binds to :80 temporarily).
# This avoids needing a dummy cert and works before nginx is running.

echo "[1/3] Requesting Let's Encrypt certificate (standalone on :80)..."
docker compose --profile https run --rm \
  --publish 80:80 \
  --entrypoint certbot \
  certbot certonly \
  --standalone \
  --preferred-challenges http \
  --keep-until-expiring \
  --non-interactive \
  --email "$EMAIL" --agree-tos --no-eff-email \
  -d "$DOMAIN" -d "$DOMAIN_WWW"

echo "[2/3] Starting HTTPS proxy (nginx) + app stack..."
docker compose --profile https up -d --build proxy

echo "[3/3] Starting auto-renew container..."
docker compose --profile https up -d certbot

echo "Done. Verify: https://${DOMAIN}/ (and https://${DOMAIN_WWW}/)"
