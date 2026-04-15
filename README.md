# FinTrack Pro

Self-hosted personal finance and investment tracking. Java 21 + Spring Boot
backend, React + Vite frontend, PostgreSQL, Redis, Nginx. Runs on a single
Docker Compose stack. Designed for one owner with optional external HTTPS
access.

## Features

- **Portfolio tracking** — stocks, crypto, TEFAS funds, pension (BES) funds,
  currencies, gold. Live prices from CoinGecko, TEFAS, and an exchange-rate
  provider (with a keyless fallback for FX).
- **On-demand TEFAS catalog** — full Turkish fund universe is searchable by
  code or name; import any fund with one click and its prices refresh
  automatically.
- **Per-asset detail pages** — history charts pulled directly from the
  upstream providers (TEFAS `BindHistoryInfo`, CoinGecko `market_chart`) with
  7D / 30D / 90D windows. Locally recorded series is used as fallback for
  assets without an upstream history endpoint.
- **Transaction log** — every buy, sell, deposit, withdraw, rebalance, and
  BES contribution recorded as an immutable audit trail. BUY/SELL
  transactions automatically update the related holding.
- **Budget** — monthly income and expense entries with categories, per-month
  summary, and snapshot history. Trends feed the Analytics page.
- **Bills** — recurring bills with due-day tracking, payment history, and a
  monthly calendar view.
- **Analytics** — savings rate trend, income vs expense bars, portfolio
  value trend, CAGR, expense growth — all computed from captured monthly and
  daily snapshots.
- **Live dashboard** — KPI cards, portfolio performance charts, a STOMP
  price ticker that refreshes every ~30 seconds.
- **Internationalization** — Turkish and English across the entire UI.
  Locale-aware number, currency, and date formatting. Backend validation
  messages are resolved through Spring `MessageSource` bundles.
- **User settings** — currency, language, theme (light/dark/system), and
  timezone persisted per user and reflected in all formatters.
- **Reports** — per-portfolio PDF summary and per-month CSV transaction
  export.
- **Auth** — JWT access (15 min) + refresh (30 days) with rotation. Spring
  Security 6 with a DB-backed refresh token store.
- **Reverse proxy and TLS** — Nginx with HTTP to HTTPS redirect, security
  headers, and a certbot sidecar for Let's Encrypt certificates.
- **Auto-start** — Docker Compose `restart: always` plus an optional systemd
  unit that brings the stack up on boot.
- **Backups** — nightly `pg_dump` via a dedicated container or a systemd
  timer; `scripts/restore.sh` handles the inverse.

## Tech Stack

| Layer       | Technology                                        |
|-------------|---------------------------------------------------|
| Backend     | Java 21, Spring Boot 3.2, Spring Security 6, JPA  |
| Migrations  | Flyway                                            |
| Frontend    | React 18, TypeScript, Vite, Tailwind, shadcn/ui   |
| Charts      | Recharts                                          |
| State       | Zustand (client) + React Query (server)           |
| Database    | PostgreSQL 16                                     |
| Cache       | Redis 7                                           |
| Proxy       | Nginx (TLS, rate limiting, security headers)      |
| Realtime    | STOMP over WebSocket                              |
| i18n        | react-i18next + Spring MessageSource              |
| Price APIs  | CoinGecko, TEFAS, ExchangeRate-API (keyless fallback) |

## Quick Start (local)

```bash
git clone https://github.com/GTRows/finance-track.git
cd finance-track
cp .env.example .env
# Edit .env and at minimum set JWT_SECRET and POSTGRES_PASSWORD.

docker compose up -d

# Open the app
# http://localhost
```

First run applies all Flyway migrations and seeds the default asset catalog.

## Configuration

All secrets live in `.env` (never committed). See `.env.example` for the full
list. The most important variables:

```env
JWT_SECRET=...                 # openssl rand -base64 64
POSTGRES_PASSWORD=...
REDIS_PASSWORD=...
COINGECKO_API_KEY=             # optional; free public endpoint works
EXCHANGE_RATE_API_KEY=         # optional; keyless open.er-api.com fallback is used if blank
DOMAIN=your.domain             # only used by the TLS setup
SSL_EMAIL=you@example.com
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
  nginx/                     Reverse proxy config + TLS snippets
  scripts/                   Setup, backup, restore, SSL, smoke tests
  docs/                      Architecture, database, API, deployment
  docker-compose.yml         Full stack (Postgres, Redis, API, UI, Nginx)
  .env.example               Environment template
```

## Status and Roadmap

Core phases 1 to 6 are shipped. See [`tasks/TODO.md`](tasks/TODO.md) for the
current state and backlog.

## License

[MIT](LICENSE) — free to use, fork, and modify.
