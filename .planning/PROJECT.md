# FinTrack Pro

## What This Is

Self-hosted, single-owner personal finance and investment tracker. Replaces an Excel workflow with a Spring Boot + React app that tracks investment portfolios (individual + BES pension funds) with live prices, monthly income/expenses, recurring bills, and a real-time dashboard. Runs entirely on Docker on a local machine or VPS, with optional HTTPS access through Nginx.

## Core Value

The owner can stop using the spreadsheet and trust this app for their entire financial picture: live portfolio P&L, monthly cash flow, and bill tracking — all in one place, fully self-hosted.

## Requirements

### Validated

<!-- Shipped in Phases 1-22 (see tasks/TODO.md). These are locked. -->

- Auth and account hardening — Phases 1, 8 (JWT access+refresh rotation, TOTP 2FA, email verify, password reset, password change, active-sessions view, login rate limiting)
- Portfolio module — Phase 2, 9 (CRUD, holdings, transactions BUY/SELL, snapshots, allocation chart, history chart, watchlist, target allocation + drift, risk metrics)
- Live price sync — Phase 2, 9 (CoinGecko, TEFAS YAT+EMK, ExchangeRate, gold-api precious metals, Yahoo Finance for stocks/BIST; STOMP broadcast; React Query polling + flash on change)
- Budget module — Phase 3, 10 (transactions, categories, monthly summary, recurring templates, multi-currency, category rollover, receipts, tags, rule-based auto-categorization, cash flow allocator)
- Bills module — Phase 4, 11 (CRUD, pay/skip, history, calendar, daily reminder scheduler, email + push reminders, variance tracking, subscription audit)
- Dashboard + analytics — Phase 5, 12 (live KPIs, allocation, history, savings goal, debt amortization, FIRE calculator, custom date range)
- Reports & export — Phase 6, 13 (portfolio PDF, budget CSV, monthly emailed PDF, xlsx export, full JSON backup/restore)
- UX — Phase 14, 17 (Cmd/Ctrl+K command palette, bulk operations, pinned holdings, first-run wizard, PWA manifest + offline shell)
- Infrastructure integration — Phase 15 (Restic encrypted backups, Trivy scans in CI, Grafana business dashboard, Dependabot auto-merge, Postgres index audit, Traefik labels, Authelia ForwardAuth, CrowdSec audit feed, Promtail + Loki, Wazuh log compatibility, Homarr tile snippet)
- Notifications — Phase 16 (Web Push with VAPID + service worker, push reminders alongside email digest)
- Portfolio depth — Phase 18 (dividend ledger, TR capital gains report, benchmark overlay BIST 100 / S&P 500 / gold)
- Forecasting — Phase 19 (12-month cash flow projection, FIRE scenario sliders)
- Hardening — Phase 22.2 (route-level code-splitting + vendor chunk strategy)
- Coverage and tooling — partial Phase 23/24 (765 backend WebMvc tests + 168-202 frontend tests via @testing-library/react, JaCoCo gate at 60%/45%, Spotless + Google Java Format, springdoc OpenAPI + Swagger UI, ESLint no-floating-promises + consistent-type-imports as errors, FlywayMigrationTest gated on Docker availability, security headers DSL, TOTP recovery codes service layer, sensitive-endpoint rate limiting, refresh-token replay protection)
- Docs — Track H mostly shipped (ARCHITECTURE.md sequence diagrams, OPERATIONS.md runbook, THREAT_MODEL.md STRIDE, QUICK_START.md walkthrough)

### Active

<!-- Current backlog from tasks/ROADMAP.md, ordered by the suggested phasing. -->

- [ ] Phase 23 — Coverage completion: A2 (broaden @DataJpaTest + Testcontainers across remaining repositories), A7 (PIT mutation testing on service layer, target 60%), A8 (frontend↔backend contract tests via OpenAPI generator or Pact), A9 (receipt OCR via tess4j as a background job)
- [ ] Phase 24 — Security hardening: D2 (Argon2id password hashing migration), D4 (WebAuthn / passkeys), D6 (refresh-token session fingerprint binding), D7 (audit_log retention + PII redaction), D8 (signed URL scheme for receipts), D9 (OWASP Dependency Check in CI failing on new CRITICAL CVEs)
- [ ] Phase 25 — Architecture cleanup: C1 (ApplicationEventPublisher for cross-cutting events), C2 (Spring Cache + Caffeine for hot reads with explicit invalidation)
- [ ] Phase 26 — Observability: E1 (OpenTelemetry OTLP to Tempo/Grafana), E2 (self-hosted Sentry/GlitchTip), E3 (SLI/SLO dashboard with burn-rate alerts)
- [ ] Phase 27 — Tax & accounts: G1 (TR tax helper — annual stoppage + capital-gains threshold), G2 (bank account entity + multi-currency + emergency-fund linkage), G3 (Garanti / Is Bankasi / Akbank CSV import behind preview+commit)
- [ ] Phase 28 — Rebalance + emergency fund: G11 (emergency-fund coverage tile), G12 (rebalance executor turning drift into one-click transactions)
- [ ] Phase 29 — Portfolio analytics: G4 (portfolio comparison), G5 (asset correlation matrix), G6 (Monte Carlo net-worth projection)
- [ ] Phase 30 — Performance & polish: F1 (N+1 audit + @EntityGraph), F2 (pg_stat_statements EXPLAIN pass + indexes), F3 (virtualized transaction list)
- [ ] Tech debt drain (CONCERNS.md): non-blocking refactor of price clients off `WebClient.block()` and `Thread.sleep` inside `@Transactional`; per-asset delta WebSocket broadcast; AuditService coverage for portfolio/budget/bill mutations; CORS bound to `CORS_ALLOWED_ORIGINS` with prod-fail-fast; Redis password required in `production` profile

### Out of Scope

- Multi-user / family sharing — single-owner design baked into the schema; would require auditing every ownership check and adding a tenant column
- Native mobile apps (React Native or otherwise) — PWA + responsive layout cover mobile usage
- TimescaleDB — premature optimization at single-user scale; plain Postgres + snapshots is sufficient
- Crypto custody / hot-wallet key storage — read-only address tracking only (G8 in backlog) is the limit
- Broker order execution — keeps the security posture data-in-only
- Full double-entry accounting ledger — the app is a personal finance tracker, not bookkeeping software
- AI / Claude integration (the `ai/` package and `CLAUDE_*` env vars) — Phase 7 cancelled; no Anthropic key provisioned

## Context

- Brownfield: Phases 1-22 are shipped end-to-end and running in production on the owner's homelab. Coverage stack: 765 backend WebMvc tests, ~200 frontend tests, JaCoCo gate at 60% instruction / 45% branch, Spotless format gate, ESLint zero-warning gate, Trivy + Dependabot in CI.
- Tech stack is fixed (see `.planning/codebase/STACK.md`): Java 21 + Spring Boot 3.2, React 18 + Vite + TypeScript strict, PostgreSQL 16 (Flyway), Redis 7, Docker Compose, Nginx, JWT (15 min access / 30 d refresh) + TOTP, STOMP WebSocket for live prices.
- Detailed code map lives in `.planning/codebase/` (STACK, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, INTEGRATIONS, CONCERNS).
- Roadmap source of truth is `tasks/ROADMAP.md` (tracks A–H + suggested Phases 23–30); `tasks/TODO.md` is the per-phase task ledger; `tasks/LESSONS.md` captures post-mortems.
- Deployment target is a unified homelab: Portainer + Traefik + Authelia + WireGuard + CrowdSec + ModSecurity + Trivy + Wazuh + Prometheus/Grafana/Loki + Alertmanager + Homarr + Restic + Syncthing. FinTrack integrates as one of the apps behind that stack.
- Operating mode: owner uses the app daily; Claude executes the backlog autonomously per project memory.

## Constraints

- **Tech stack**: Java 21, Spring Boot 3.2, React 18, PostgreSQL 16, Redis 7, Docker Compose — fixed. New code must fit, not introduce parallel stacks.
- **Schema**: Flyway-only, append-only `V{n}__*.sql`. Never edit a released migration; Hibernate `ddl-auto=validate`.
- **Security**: Stateless JWT with rotating refresh tokens; TOTP enforced for sensitive flows; secrets only via env (`.env`); production profile must reject permissive CORS, missing JWT secret, and missing Redis password.
- **Single-tenant**: Every entity is owner-scoped; multi-tenancy is explicitly out of scope.
- **Performance budget**: Single-user / homelab scale — keep Postgres + snapshots, no premature distributed-systems gear.
- **Compliance**: GDPR-aligned audit log retention with PII redaction (Phase 24 D7).
- **No AI integration**: `ai/` package and `CLAUDE_*` configuration must remain absent (Phase 7 cancelled).
- **CI gates**: JaCoCo 60% instruction / 45% branch, Spotless format, ESLint `--max-warnings 0`, Trivy HIGH/CRITICAL — gates may not be bypassed.
- **Code style**: No comments on unchanged code; English-only identifiers/comments; Turkish only in user-facing translations (`frontend/src/i18n/`).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Single-owner architecture (no multi-tenancy) | Owner is the only user; multi-user costs every service-level audit | Good — keeps surface area small |
| JWT with refresh-token rotation in DB (not stateless-only) | Enables revocation, "log out other sessions", and replay protection | Good |
| Refresh token in localStorage, not httpOnly cookie | CSP + no third-party scripts is judged sufficient; cookies cause cross-origin friction | Revisit — flagged in `docs/THREAT_MODEL.md` and CONCERNS.md as residual XSS exposure |
| Flyway forward-only migrations, Hibernate `validate` | Schema authored as SQL, not derived from entities; avoids drift | Good |
| STOMP WebSocket for live prices | Cleaner than SSE/long-poll; pairs with Spring's broker | Good |
| Reactive `WebClient.block()` for price clients | Quick to ship, fits the synchronous scheduler model | Revisit — see CONCERNS.md; switch to virtual threads or async composition before scaling external calls |
| Cancel AI / Claude integration (Phase 7) | No API key provisioned; feature not part of the personal-use scope | Good |
| Restic over plain `pg_dump` for backups | Encrypted, deduplicated, S3/B2/REST targets | Good |
| Traefik + Authelia integration alongside bundled Nginx | Aligns with the rest of the homelab; Nginx kept as a fallback profile | Good |
| 60% instruction / 45% branch JaCoCo floor | Realistic for a solo project; high enough to catch regressions | Good — currently at 77.5% / 62.6% |
| TR-first localization with English fallback | Owner-language is Turkish; English keeps the project shareable | Good |

---
*Last updated: 2026-05-04 after initialization*
