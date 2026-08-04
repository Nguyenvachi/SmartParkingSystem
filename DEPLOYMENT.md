# Optional HTTPS Deployment Reference

This document is a configuration reference for deploying the portfolio project on a Linux host. It is not a production-readiness claim.

## Requirements

- Linux host with Docker Engine and Docker Compose
- A domain you control
- Ports 80 and 443 available

## Configuration

Copy the VPS template and replace every placeholder:

```bash
cp .env.vps.example .env
```

Required values include:

```env
APP_FRONTEND_BASE_URL=https://parking.example.com
APP_CORS_ALLOWED_ORIGINS=https://parking.example.com
APP_WS_ALLOWED_ORIGIN_PATTERNS=https://parking.example.com

MYSQL_ROOT_PASSWORD=CHANGE_ME_STRONG
MYSQL_USERNAME=root
APP_JWT_SECRET=CHANGE_ME_WITH_AT_LEAST_32_RANDOM_CHARACTERS
APP_QR_SECRET=CHANGE_ME_WITH_A_DIFFERENT_32_CHARACTER_VALUE

APP_PASSWORD_RESET_EXPOSE_TOKEN=false
```

Generate independent secrets outside the repository, for example with `openssl rand -hex 32`. OAuth, SMTP, MoMo, and VNPay values remain optional unless the corresponding integration is enabled.

## Start

The repository contains an optional Nginx/Certbot Compose profile and an initialization helper under `scripts/vps`.

```bash
docker compose --profile https up -d --build
```

Before exposing the host, verify firewall rules, HTTPS certificates, CORS origins, callback URLs, backups, and container logs. Do not expose MySQL, Mailpit, or phpMyAdmin publicly.
