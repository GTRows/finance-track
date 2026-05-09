# FinTrack Pro

Self-hosted personal finance and investment tracking. Java 21 + Spring Boot
backend, React + Vite frontend, PostgreSQL, Redis, Nginx. Runs on a single
Docker Compose stack. Designed for one owner with optional external HTTPS
access.

Current release: **v1.4.0** (see [`CHANGELOG.md`](CHANGELOG.md)).

## Features

### Portfolio and investments
- **Portfolio tracking** for stocks, crypto, TEFAS funds, pension (BES) funds,
  currencies, and gold. Live prices from CoinGecko, TEFAS, an exchange-rate
  provider (with a keyless fallback for FX), Yahoo Finance for stocks/BIST,
  and gold-api for precious metals. Provider calls fan out on virtual threads
  with a parallelism cap; balances and holdings update via
  `@TransactionalEventListener(AFTER_COMMIT)` listeners.
- **On-demand TEFAS catalog** — search the full Turkish fund universe by code
  or name; import any fund with one click and its prices refresh
  automatically.
- **Per-asset detail pages** — history charts pulled directly from the
  upstream providers (TEFAS `BindHistoryInfo`, CoinGecko `market_chart`) with
  7D / 30D / 90D windows. Locally recorded series is the fallback for assets
  without an upstream history endpoint.
- **Transaction log** — every buy, sell, deposit, withdraw, rebalance, and
  BES contribution recorded as an immutable audit trail. BUY/SELL
  transactions automatically update the related holding via cross-cutting
  events.
- **Target allocation and drift** — define per-bucket target weights, see
  drift versus targets, and one-click rebalance: the executor produces
  `BUY`/`SELL` suggestions with a configurable drift threshold, scales
  proportionally on cash-shortfall, and commits selected rows in one
  `@Transactional` shot through `InvestmentTransactionService.record(...)`.
- **Dividend ledger and TR capital-gains report** — per-portfolio history of
  declared/received dividends and a year-end capital-gains roll-up.
- **Benchmark overlay** — overlay any portfolio against BIST 100, S&P 500,
  or gold over the selected window.

### Budget, bills, and accounts
- **Budget** — monthly income and expense entries with categories, per-month
  summary, snapshot history, multi-currency, category rollover, receipts,
  tags, and rule-based auto-categorisation that the bank-import flow reuses.
- **Bills** — recurring bills with due-day tracking, payment history, a
  monthly calendar view, daily reminder scheduler, email + Web Push
  reminders, variance tracking, and a subscription audit page.
- **Accounts entity** — bank checking / savings, brokerage cash, crypto
  wallets, and physical cash modeled as first-class accounts with
  multi-currency `NUMERIC(20,8)` balances. Transactions, investment
  transactions, and bill payments all carry an optional `account_id`; the
  account balance recomputes on `AFTER_COMMIT` listeners through a
  `REQUIRES_NEW` updater so the rollup write commits independently.
- **TR bank CSV import** — preview-and-commit import for Garanti BBVA,
  Turkiye Is Bankasi, and Akbank statements. Encoding / delimiter / date /
  decimal / sign quirks are encoded per bank; SHA-256 row fingerprints make
  re-imports idempotent; best-effort categorisation runs through the same
  rule engine the manual transaction form uses.
- **Emergency-fund tile** — dashboard tile with red < 3 / amber 3-6 / green
  > 6 banding (configurable per user) measuring `accounts.currentBalance`
  filtered by user-selected types against trailing-12-month average expense.

### Analytics and reporting
- **Live dashboard** — KPI cards, portfolio performance charts, a STOMP
  price ticker that refreshes every ~30 seconds and broadcasts only the
  per-asset deltas above a 0.01 % relative tolerance.
- **Portfolio comparison** — side-by-side multi-portfolio overlay with
  percent-change vs absolute mode toggle, multi-select picker, and date
  range presets.
- **Asset correlation matrix** — Pearson (default) or Spearman correlations
  on log-returns, pair-wise date intersection alignment, CSS-grid heatmap
  with hover tooltip.
- **Monte Carlo net-worth projection** — 10 000-iteration simulation across
  a per-class allocation editor (weight / mean / stddev), iterations /
  horizon / monthly-contribution inputs, and a Recharts area-fan chart of
  p10 / p25 / p50 / p75 / p90 percentile bands plus four summary cards.
- **TR tax helper** — annual dividend stoppage aggregate and capital-gains
  threshold warnings with 80%/100% banding, year-keyed YAML parameters so
  the owner edits one block per January.
- **Cash flow projection** — 12-month forward projection plus FIRE scenario
  sliders.
- **Reports and export** — per-portfolio PDF summary, per-month CSV
  transaction export, monthly emailed PDF, xlsx export, and full JSON
  backup/restore.

### Auth and security
- **JWT access (15 min) + refresh (30 days)** with rotation and a DB-backed
  refresh-token store; refresh tokens carry a UA + IP-prefix SHA-256
  fingerprint that revokes mismatched sessions on next refresh.
- **TOTP 2FA** with recovery codes, **WebAuthn passkeys**
  (`webauthn4j-core`), and **Argon2id** as the default password encoder
  with rehash-on-login fallback for legacy bcrypt rows.
- **Audit log** with PII redaction, scheduled retention worker, and signed
  HMAC-SHA256 receipt URLs (5-minute TTL) for the receipt thumbnail path.
- **Production fail-fast guard** rejects boot when CORS, Redis password,
  JWT secret, receipt secret, WebAuthn config, or `SENTRY_DSN` are
  misconfigured in the `production` profile.

### Observability and operations
- **OpenTelemetry OTLP traces** to a self-hosted `grafana/tempo:2.6.0`
  container; controllers, service boundaries, and external HTTP clients
  auto-instrumented; trace context propagates across the virtual-thread
  price fan-out via a `ContextSnapshot` decorator. `traceId`/`spanId`
  written to MDC alongside `requestId` so Loki log fields correlate
  one-to-one with traces.
- **Self-hosted GlitchTip** (Sentry-wire-compatible) shipped as a compose
  overlay; release-tagged with `IDENTITY.yaml` version, PII scrubbed via
  the same redactor as the audit log, and trace IDs cross-linked.
- **SLI/SLO dashboard with burn-rate alerts** — HTTP latency p95 per route
  group, HTTP error rate, and per-source price-sync freshness, all on a
  provisioned Grafana dashboard with a Google-SRE-workbook two-burn-rate
  alert envelope.
- **`pg_stat_statements` operator runbook** — opt-in monitoring overlay
  plus query templates for slow-query inspection.
- **N+1 audit and FK-index coverage regression tests** — Hibernate
  Statistics-backed query-count caps and a Docker-gated test that pins
  every FK column has a backing index via `pg_indexes` +
  `information_schema.referential_constraints`.

### Frontend polish
- **Cmd / Ctrl+K command palette**, **bulk operations**, **pinned
  holdings**, **first-run wizard**, **PWA manifest + offline shell**.
- **Virtualized transaction list** — `@tanstack/react-virtual` kicks in
  above 1000 rows; below that, the existing layout/sort/select behaviour
  is preserved unchanged.
- **Internationalization** — Turkish and English across the entire UI.
  Locale-aware number, currency, and date formatting. Backend validation
  messages resolved through Spring `MessageSource` bundles.
- **User settings** — currency, language, theme (light/dark/system), and
  timezone persisted per user and reflected in all formatters.

### Infrastructure
- **Reverse proxy and TLS** — Nginx with HTTP to HTTPS redirect, security
  headers, and a certbot sidecar for Let's Encrypt certificates.
- **Auto-start** — Docker Compose `restart: always` plus an optional
  systemd unit that brings the stack up on boot.
- **Backups** — Restic encrypted backups with S3 / B2 / REST targets, plus
  a nightly `pg_dump` fallback path.
- **Optional homelab integrations** — Traefik + Authelia ForwardAuth,
  CrowdSec audit feed, Promtail + Loki, Wazuh log compatibility, Trivy +
  Dependabot in CI, Grafana business dashboard, Homarr tile snippet.

## Tech Stack

| Layer       | Technology                                                       |
|-------------|------------------------------------------------------------------|
| Backend     | Java 21 (virtual threads), Spring Boot 3.2, Spring Security 6, JPA |
| Migrations  | Flyway (V1..V47, append-only)                                    |
| Frontend    | React 18, TypeScript strict, Vite, Tailwind CSS, shadcn/ui       |
| Charts      | Recharts                                                         |
| Virtualization | @tanstack/react-virtual                                       |
| State       | Zustand (client) + React Query (server)                          |
| Database    | PostgreSQL 16                                                    |
| Cache       | Redis 7 (sessions, refresh-token store, rebalance proposals); Caffeine in-process for hot reads |
| Proxy       | Nginx (TLS, rate limiting, security headers)                     |
| Realtime    | STOMP over WebSocket (per-asset price deltas)                    |
| Tracing     | OpenTelemetry SDK -> grafana/tempo                               |
| Errors      | sentry-spring-boot-starter-jakarta -> self-hosted GlitchTip      |
| Metrics     | Micrometer + Prometheus + Grafana + Alertmanager                 |
| i18n        | react-i18next + Spring MessageSource                             |
| Price APIs  | CoinGecko, TEFAS YAT+EMK, ExchangeRate-API (keyless fallback), Yahoo Finance, gold-api |

## Quick Start (local)

```bash
git clone https://github.com/GTRows/finance-track.git
cd finance-track
cp .env.example .env
# Edit .env and at minimum set JWT_SECRET, POSTGRES_PASSWORD, REDIS_PASSWORD.

docker compose up -d

# Open the app
# http://localhost
```

First run applies all Flyway migrations and seeds the default asset catalog.

Optional monitoring overlays (each is a separate compose `-f` file, safe to
omit on a small homelab):

```bash
# Tracing (Tempo + Grafana datasource)
docker compose -f docker-compose.yml -f monitoring/glitchtip/docker-compose.glitchtip.yml up -d

# SLO alerting (Prometheus rules + Alertmanager)
docker compose -f docker-compose.yml -f monitoring/prometheus/docker-compose.prometheus.yml up -d

# Slow-query inspection (pg_stat_statements)
docker compose -f docker-compose.yml -f monitoring/postgres/docker-compose.postgres.yml up -d
```

## Configuration

All secrets live in `.env` (never committed). See `.env.example` for the full
list. The most important variables:

```env
JWT_SECRET=...                 # openssl rand -base64 64
POSTGRES_PASSWORD=...
REDIS_PASSWORD=...              # required in the production profile
COINGECKO_API_KEY=              # optional; free public endpoint works
EXCHANGE_RATE_API_KEY=          # optional; keyless open.er-api.com fallback used if blank
DOMAIN=your.domain              # only used by the TLS setup
SSL_EMAIL=you@example.com
CORS_ALLOWED_ORIGINS=https://your.domain
SENTRY_DSN=                     # optional; required when running the GlitchTip overlay
FINTRACK_RELEASE_VERSION=       # optional; overrides IDENTITY.yaml for release tagging
```

## HTTPS and External Access

1. Point your domain at the host.
2. Set `DOMAIN` and `SSL_EMAIL` in `.env`.
3. Run `scripts/ssl-setup.sh` to issue the first certificate. Staging mode
   is available via the `STAGING=1` flag for dry-runs.
4. Open TCP 443 on the firewall / router. `nginx` auto-reloads as certbot
   renews.

Detailed instructions live in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Auto-start on Boot

`docker compose up -d` with `restart: always` is usually enough. For full
systemd control:

```bash
sudo cp scripts/fintrack.service /etc/systemd/system/
sudo systemctl enable --now fintrack
```

Nightly backups via systemd:

```bash
sudo cp scripts/fintrack-backup.service scripts/fintrack-backup.timer /etc/systemd/system/
sudo systemctl enable --now fintrack-backup.timer
```

## Smoke Test

```bash
./scripts/smoke-test.sh http://localhost
# or on Windows
pwsh ./scripts/smoke-test.ps1 -BaseUrl http://localhost
```

The script registers a user, exercises the core endpoints, and reports any
non-2xx responses. The matching manual UI checklist lives in
[`docs/SMOKE_TEST.md`](docs/SMOKE_TEST.md).

## Project Layout

```
fintrack/
  backend/                   Spring Boot app (feature-based packages)
  frontend/                  React + Vite app
  monitoring/                Compose overlays for Tempo / GlitchTip / Prometheus / Postgres
  nginx/                     Reverse proxy config + TLS snippets
  scripts/                   Setup, backup, restore, SSL, smoke tests
  docs/                      Architecture, database, API, deployment, operations, threat model
  .planning/                 GSD planning artefacts (roadmap, per-phase PLAN/SUMMARY)
  docker-compose.yml         Core stack (Postgres, Redis, API, UI, Nginx)
  .env.example               Environment template
```

## Status

All planned roadmap phases (1 through 30) are shipped. The project is
feature-complete against its original scope; further work happens as
deferred-issue cleanup. See [`CHANGELOG.md`](CHANGELOG.md) for the per-release
breakdown and [`tasks/ROADMAP.md`](tasks/ROADMAP.md) for the original backlog
and what remains in the "won't do / deferred" pile.

## License

[MIT](LICENSE) — free to use, fork, and modify.
