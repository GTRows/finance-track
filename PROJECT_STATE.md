# FinTrack Pro — Project State

Persistent working memory for autonomous development. Updated after each meaningful milestone.

## Project Goal

Self-hosted, single-user personal finance & investment tracking app replacing an Excel
spreadsheet. Must ship to production Docker on a Linux VPS with HTTPS (Let's Encrypt),
auto-start on boot (systemd), daily pg_dump backups, and survive real usage.

## Tech Stack

- Backend: Java 21, Spring Boot 3.2, Spring Security 6, Spring Data JPA, Flyway
- Frontend: React 18, TypeScript, Vite, Tailwind, shadcn/ui, Recharts, Zustand, React Query, react-i18next
- DB: PostgreSQL 16, Redis 7
- Infra: Docker Compose, Nginx (TLS + rate limit), systemd
- Auth: JWT access (15m) + refresh (30d) with DB-backed refresh tokens
- Reports: OpenPDF for PDFs, plain CSV for transactions

## Completed Phases

- [x] Phase 1 — Foundation (auth, infrastructure, frontend scaffolding)
- [x] Phase 2 — Portfolio module (CRUD, holdings, snapshots, live prices via CoinGecko/TEFAS/ExchangeRate, WebSocket broadcasting)
- [x] Phase 3 — Budget module (transactions, categories, monthly summaries)
- [x] Phase 4 — Bills module (CRUD, payment tracking, history)
- [x] Phase 5 — Dashboard + i18n (Turkish/English), AppShell, Settings
- [x] Phase 6 — Reports & Export (PDF portfolio, CSV budget)

## Current Focus

Cleaning up deferred items from Phases 2-5 and hardening for production:
1. Transaction log (Phase 2)
2. Monthly log view + snapshot button (Phase 3)
3. Bill reminder scheduler + bills calendar (Phase 4)
4. Live price ticker, analytics page, theme toggle, mobile responsive polish (Phase 5)
5. Phase 7 AI Analysis (optional)
6. Production deployment verification (Docker stack boots end-to-end, nginx, SSL, backups)

## Backlog (prioritized)

- [x] Transaction log (UI + API; records update holding qty/avg cost automatically)
- [x] Monthly summary log table + snapshot button in BudgetPage
- [x] Bill reminder scheduler (daily 08:00 check, logs unpaid bills within remind window)
- [x] BillsCalendar component (month grid)
- [x] Theme toggle (dark/light/system) wired to user_settings via settings API
- [x] Mobile responsive audit (sidebar drawer, hamburger, responsive padding; tables already use overflow-x-auto)
- [x] LivePriceTicker (STOMP subscription drives zustand store, dashboard shows ticker strip)
- [x] AnalyticsPage (savings rate, income/expense, portfolio value; avg savings, expense growth, CAGR KPIs)
- [ ] AI analysis endpoint (Claude API) — skipped (no API key available)
- [x] Localization hardening (locale-aware formatters, currency selector, MessageSource + validation bundles, timezone in settings)
- [ ] Excel import (Yatirim_Takip_final_v2.xlsx)
- [x] SSL setup wired for self-hosted deployment (fatihaciroglu.dev, certbot service + webroot renewals)
- [x] Automated smoke test (scripts/smoke-test.sh) + manual UI checklist (docs/SMOKE_TEST.md)
- [x] Self-hosted deployment guide (docs/DEPLOYMENT.md) + daily backup systemd timer + restore script

## Architectural Decisions

- Feature-based Java packages (self-contained controllers/services/repos/DTOs per feature)
- DTOs are Java records, all endpoints return ResponseEntity<T>
- React Query for server state, Zustand for client UI state
- Price sync uses webflux WebClient; schedulers push to `/topic/prices` WebSocket after each cycle
- Auth: refresh tokens stored in DB (supports revocation, not stateless)
- Reports use OpenPDF (LGPL, no license cost) — not iText 7 (AGPL)
- i18n: Turkish and English, user preference in user_settings (language column)

## Known Gotchas

- CRLF line endings on Windows checkout — Git auto-converts; do not force LF
- `com.lowagie.text.List` collides with `java.util.List`; must import PDF classes explicitly, never wildcard
- TEFAS fund codes live in V7 migration; endpoint at `/api/DB/BindHistoryInfo`
- API base path is `/api/v1/...`; SecurityConfig PUBLIC_PATHS must list exact prefixes
- `@types/node` required on frontend for vite config

## Next Action

This Windows 11 host is now the production target. Remaining runbook:
1. Fill `.env`, then `docker compose up -d --build`.
2. `powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1` — must be green.
3. Router port-forward 80/443 to this host; firewall allow 80/443 inbound.
4. DNS A records for `fatihaciroglu.dev` + `www` to the public IP.
5. `bash scripts/ssl-setup.sh` once DNS propagates; certbot service handles renewal.
6. Run manual UI checklist in `docs/SMOKE_TEST.md`.

Backups run inside the compose stack via the `backup` service — no Windows
Task Scheduler / systemd needed. Optional follow-up: Excel import.
