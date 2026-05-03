# External Integrations

**Analysis Date:** 2026-05-03

## APIs and External Services

**Crypto prices — CoinGecko:**
- Used for: BTC, ETH (extensible)
- Endpoint: `https://api.coingecko.com/api/v3/simple/price`
- Auth: Optional API key (free tier rate-limited)
- Env: `COINGECKO_API_KEY`
- Client: `backend/src/main/java/com/fintrack/price/client/CoinGeckoClient.java`

**Turkish funds — TEFAS:**
- Used for: Local mutual / pension funds (TTA, ITP, TIE, TMG, TI1, ABE, AH5, BHT, BGL, AH3 …)
- Endpoint: `https://www.tefas.gov.tr/api/DB/BindHistoryInfo` (POST form-encoded)
- Auth: None (public data)
- Client: `backend/src/main/java/com/fintrack/price/client/TefasClient.java`
- Notes: Sequential requests with throttling delay (~150 ms between calls)

**FX rates — ExchangeRate-API:**
- Used for: USD/TRY, EUR/TRY, GBP/TRY conversions
- Endpoint: `https://v6.exchangerate-api.com/v6/{KEY}/pair/{BASE}/{TARGET}`
- Auth: API key
- Env: `EXCHANGE_RATE_API_KEY`
- Client: `backend/src/main/java/com/fintrack/price/client/ExchangeRateClient.java`

**Precious metals — gold-api.com:**
- Used for: XAU, XAG, XPT, XPD
- Endpoint: `https://api.gold-api.com/price/{SYMBOL}`
- Auth: None
- Client: `backend/src/main/java/com/fintrack/price/client/PreciousMetalsClient.java`

**Stocks (incl. BIST) — Yahoo Finance (unofficial):**
- Used for: Equities, BIST tickers via `.IS` suffix (e.g. `THYAO.IS`)
- Endpoint: `https://query1.finance.yahoo.com/v8/finance/chart/{SYMBOL}`
- Auth: None
- Client: `backend/src/main/java/com/fintrack/price/client/YahooFinanceClient.java`

**AI insights — Anthropic Claude (optional, opt-in):**
- Endpoint: `https://api.anthropic.com/v1`
- Env: `CLAUDE_API_KEY`, `CLAUDE_ENABLED` (default `false`), `CLAUDE_MODEL`
- Module: `backend/src/main/java/com/fintrack/ai/`

**Sync schedule:** Live refresh every 30 seconds (crypto, FX, metals, stocks); funds refreshed hourly. Coordinated by `backend/src/main/java/com/fintrack/price/PriceSyncService.java` and the scheduler under `backend/src/main/java/com/fintrack/scheduler/`.

## Data Storage

**PostgreSQL 16 (alpine):**
- Service `postgres` in `docker-compose.yml`
- Connection via `SPRING_DATASOURCE_URL` (HikariCP, max-pool-size 10)
- Schema managed by Flyway (`backend/src/main/resources/db/migration/V*.sql`)
- Hibernate `ddl-auto=validate` (Flyway is the source of truth)

**Redis 7 (alpine):**
- Service `redis` in `docker-compose.yml`
- Auth via `SPRING_REDIS_PASSWORD` / `REDIS_PASSWORD`
- Use cases: price cache (TTL ~5 min), session helpers, login rate-limit counters
- Eviction: `allkeys-lru`, AOF persistence

## Authentication

**Internal JWT (no external IdP):**
- Algorithm: HS256
- Access token: 15 min (kept in memory in Zustand)
- Refresh token: 30 days (rotated on every refresh)
- Refresh storage: `users.refresh_tokens` table for revocation
- Implementation: `backend/src/main/java/com/fintrack/auth/JwtUtil.java`, `JwtAuthFilter.java`, `AuthService.java`
- Frontend: `frontend/src/api/client.ts` (axios interceptors), `frontend/src/store/auth.store.ts`

**TOTP 2FA (RFC 6238):**
- Setup: `POST /api/v1/auth/2fa/setup` (returns provisioning URI / QR)
- Verify: `POST /api/v1/auth/2fa/verify`
- Secret stored on `users.totp_secret`
- Implementation: `backend/src/main/java/com/fintrack/auth/TotpService.java`

**Optional Authelia SSO:**
- Forward-auth pattern via Traefik (`docker-compose.traefik.yml`)
- Enabled with `AUTHELIA_ENABLED=true`, header configured via `AUTHELIA_HEADER`

## Monitoring and Observability

**Prometheus:**
- Service `prometheus` (`prom/prometheus`)
- Scrapes `backend:8080/api/actuator/prometheus`
- Config: `monitoring/prometheus.yml`

**Grafana:**
- Service `grafana` (`grafana/grafana`)
- Provisioning: `monitoring/grafana/provisioning/`
- Dashboards: `monitoring/grafana/dashboards/`
- Login: `admin` / `GRAFANA_ADMIN_PASSWORD`

**Promtail / Loki (optional, profile `loki`):**
- Config: `monitoring/promtail.yml`
- Pushes container logs labeled `com.fintrack.log=true` to a `LOKI_URL`

**Spring Actuator:**
- Base path `/api/actuator` — `health`, `info`, `metrics`, `prometheus` exposed (see `application.yml`)

**Application logging:**
- Logback, configured under `backend/src/main/resources/` (logback.xml or yaml)
- File output to `LOG_DIR` (default `./logs` in dev, `/var/log/fintrack` in prod)
- Rotation by size (`LOG_MAX_FILE_SIZE`) and history (`LOG_MAX_HISTORY_DAYS`)

## CI/CD and Deployment

**GitHub Actions (`.github/workflows/ci.yml`):**
- Backend job: `./mvnw verify` (JUnit + JaCoCo gate)
- Frontend job: `tsc --noEmit`, `vitest`, `vite build`
- Docker job: Buildx + Trivy scans (HIGH/CRITICAL) for repo and built images

**Deployment targets:**
- Local: `docker compose up -d` (HTTP on 80, optional HTTPS on 443)
- VPS: Docker Compose + Nginx + Let's Encrypt (`scripts/ssl-setup.sh`)
- Auto-start: systemd unit `scripts/fintrack.service`

## Environment Configuration

**Development:**
- Profile `development` — Swagger UI at `/swagger-ui.html`, JPA `show-sql=true`, console + `./logs`
- Permissive CORS (`*`) — must be tightened for prod
- JWT secret: dev placeholder

**Production:**
- Profile `production` — Swagger off, structured logs to `/var/log/fintrack`
- CORS restricted to deploy domain (`CORS_ALLOWED_ORIGINS`)
- JWT secret rotated to a 256-bit random value
- Required secrets via `.env`: `JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `EXCHANGE_RATE_API_KEY`, etc.

## Backups

**Restic-based encrypted backups:**
- Service `backup` in `docker-compose.yml` (image `restic/restic`)
- Source: `pg_dump` piped to restic
- Repo: local volume by default; `RESTIC_REPOSITORY` can target S3 / B2 / REST
- Schedule: ~5 min after boot, then every `BACKUP_INTERVAL_SECONDS` (default 86400)
- Retention: 7 daily, 4 weekly, 6 monthly
- Required: `RESTIC_PASSWORD` (container exits if unset)

**Per-user export/import:**
- `GET /api/v1/backup/export` — JSON envelope of user data
- `POST /api/v1/backup/import` — destructive replace for the calling user
- Module: `backend/src/main/java/com/fintrack/backup/`

## Webhooks and Real-time

**WebSocket (STOMP):**
- Endpoint: `/ws` (upgraded HTTP)
- Topic: `/topic/prices` (broadcast, public market data)
- Backend config: `backend/src/main/java/com/fintrack/websocket/`
- Frontend hook: `frontend/src/hooks/useLivePrices.ts`, client wrapper `frontend/src/lib/stompClient.ts`

**Web Push (optional):**
- VAPID keys via `PUSH_VAPID_PUBLIC_KEY`, `PUSH_VAPID_PRIVATE_KEY`, `PUSH_SUBJECT`
- Module skeleton: `backend/src/main/java/com/fintrack/push/`

**Email (SMTP, optional):**
- Enabled when `SMTP_HOST` is set
- Implementation: `backend/src/main/java/com/fintrack/notification/`

---

*Integration audit: 2026-05-03*
*Update when adding or removing external services*
