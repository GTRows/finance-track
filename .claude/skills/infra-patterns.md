---
name: infra-patterns
description: Docker, Nginx, and deployment patterns for this project
---

# Infrastructure Patterns — FinTrack

## Docker Compose Structure

Five services in a single `fintrack-net` bridge network.
Services discover each other by container name (e.g. `http://backend:8080`).

```
nginx       → entry point, port 80/443 exposed to host
frontend    → React SPA, port 3000 (internal only)
backend     → Spring Boot API, port 8080 (internal only)
postgres    → PostgreSQL, port 5432 (internal only)
redis       → Redis, port 6379 (internal only)
```

**Never expose postgres or redis to the host** — they're internal-only.

## Backend Dockerfile

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Multi-stage build: build stage uses JDK, runtime stage uses JRE (smaller image).

## Frontend Dockerfile

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
RUN npm install -g serve
COPY --from=build /app/dist .
EXPOSE 3000
CMD ["serve", "-s", ".", "-l", "3000"]
```

## Environment Variable Passing

Docker Compose reads from `.env` file automatically.
Variables are passed to containers via `environment:` section.
Never bake secrets into Docker images.

```yaml
backend:
  environment:
    - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
    - JWT_SECRET=${JWT_SECRET}
```

## Health Checks

Every service has a health check. Backend has `start_period: 60s` because Spring Boot takes time to start.

```yaml
backend:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 60s  # Give Spring Boot time to start
```

`depends_on` with `condition: service_healthy` ensures startup order:
backend waits for postgres + redis to be ready.

## Auto-Start on Windows

Docker Desktop on Windows starts Docker Engine automatically if "Start Docker Desktop when you log in" is enabled.
With `restart: always`, containers restart when Docker Engine starts.

No systemd needed on Windows. On Linux VPS, use `systemd`:
```ini
# /etc/systemd/system/fintrack.service
[Unit]
Description=FinTrack Pro
After=docker.service
Requires=docker.service

[Service]
WorkingDirectory=/opt/fintrack
ExecStart=/usr/bin/docker compose up
ExecStop=/usr/bin/docker compose down
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## Nginx Routing Rules

```nginx
# Auth — strict rate limit (brute force protection)
location /api/auth/ {
    limit_req zone=auth_limit burst=3 nodelay;
    proxy_pass http://backend:8080/api/auth/;
}

# API — standard rate limit
location /api/ {
    limit_req zone=api_limit burst=20 nodelay;
    proxy_pass http://backend:8080/api/;
}

# WebSocket — no rate limit, upgrade connection
location /ws/ {
    proxy_pass http://backend:8080/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;  # Keep WS connections alive
}

# Frontend SPA — all routes → index.html
location / {
    proxy_pass http://frontend:3000/;
}
```

## SSL Setup (Production)

Run once on the VPS:
```bash
# Install certbot
apt install certbot python3-certbot-nginx

# Get certificate (domain must point to server)
certbot --nginx -d yourdomain.com --email your@email.com --agree-tos

# Certbot auto-renews via cron — verify:
certbot renew --dry-run
```

Then uncomment SSL blocks in `nginx/nginx.conf`.

## Windows → VPS Migration

No code changes needed. Only environment changes:
1. `DOMAIN=yourdomain.com` in `.env`
2. Run `scripts/ssl-setup.sh`
3. Uncomment HTTPS blocks in `nginx/nginx.conf`
4. Set firewall: only 80 and 443 open (not 8080, 5432, 6379)

## Database Backups

```bash
# scripts/backup.sh
BACKUP_DIR="./backups"
DATE=$(date +%Y%m%d_%H%M%S)
FILENAME="fintrack_${DATE}.sql.gz"

docker compose exec -T postgres pg_dump -U $POSTGRES_USER $POSTGRES_DB \
  | gzip > "${BACKUP_DIR}/${FILENAME}"

# Keep only last 30 days
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete

echo "Backup saved: $FILENAME"
```

## Data Volumes

Named volumes survive `docker compose down`. Destroyed only with `docker compose down -v`.

```yaml
volumes:
  postgres-data:   # PostgreSQL data — NEVER delete in production
  redis-data:      # Redis persistence
  nginx-certs:     # Let's Encrypt certificates
```

## AWS Migration Path (future)

When migrating to AWS, these components map to:
- `nginx` → ALB (Application Load Balancer) with ACM certificate
- `frontend` → S3 + CloudFront (static hosting)
- `backend` → ECS Fargate (container)
- `postgres` → RDS PostgreSQL
- `redis` → ElastiCache Redis

Application code doesn't change — only infrastructure configuration.
