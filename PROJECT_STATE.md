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
- [ ] Monthly summary log table + snapshot button in BudgetPage
- [ ] Bill reminder scheduler (notification pings N days before due)
- [ ] BillsCalendar component (month grid)
- [ ] Theme toggle (dark/light) wired to user_settings
- [ ] Mobile responsive audit (sidebar collapse, table scrolling)
- [ ] AnalyticsPage (trends: savings rate, expense growth, portfolio CAGR)
- [ ] AI analysis endpoint (Claude API) — optional, gated on CLAUDE_ENABLED
- [ ] Localization hardening (locale-aware number/date formatting, currency selection, backend MessageSource, timezone)
- [ ] Excel import (Yatirim_Takip_final_v2.xlsx)
- [ ] Production SSL setup run-through on a real VPS
- [ ] End-to-end smoke test: docker compose up -d, register, add holding, see price tick

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

Move to deferred Phase 3 item: Monthly log section + snapshot button in BudgetPage (small, self-contained).
