# FinTrack Pro - Post-v1 Roadmap

All phase 1-19 and 22 work from `TODO.md` is shipped. This document lists
credible next work, ordered by blast radius and effort. Each item is scoped
enough to turn into a phase of its own.

Scoring:

- **Effort**: S = <1 day, M = 1-3 days, L = 1 week+
- **Impact**: low / med / high
- Items under "Won't do" are intentionally skipped; don't re-add them.

---

## Track A - Close existing gaps (tests, debt)

These are hygiene, not features. They stop regressions but change nothing a
user would see.

| # | Item | Effort | Impact |
|---|------|--------|--------|
| A1 | `@WebMvcTest` suites for every `@RestController` (request shape, validation errors, auth rules, problem-details) | M | med |
| A2 | `@DataJpaTest` + Testcontainers Postgres for all `*Repository` query methods | M | med |
| A3 | Frontend: add `@testing-library/react` and write hook tests for every `useX` that wraps React Query | M | med |
| A4 | Frontend: component tests for critical dialogs (`AddTransactionDialog`, `AddPortfolioDialog`, `PayBillDialog`) and pages (`LoginPage`, `DashboardPage`, `BudgetPage`) | L | med |
| A5 | Enforce JaCoCo line/branch minimums in `pom.xml` (`jacoco:check` in CI, fail below 70%/60%) | S | low |
| A6 | Fix `FlywayMigrationTest` on Windows hosts: document Docker Desktop socket setup or swap to embedded Postgres via `pg-embedded` | S | low |
| A7 | Mutation testing with PIT (`pitest-maven`), target 60% mutation score on service layer | M | med |
| A8 | Contract tests between frontend `*.api.ts` modules and Spring REST endpoints (openapi-generator or Pact) | L | high |
| A9 | Receipt OCR (noted as follow-up in phase 10.4) using Tesseract via tess4j, run as a background job | L | low |

---

## Track B - Code quality & DX

| # | Item | Effort | Impact |
|---|------|--------|--------|
| B1 | Spotless + Checkstyle in `pom.xml` with Google Java Format, fail build on violation | S | med |
| B2 | ESLint rules tightening: `no-floating-promises`, `no-unsafe-*`, `consistent-type-imports`; all warnings to errors | S | med |
| B3 | Pre-commit hooks via Husky: run ESLint, Prettier, typecheck on staged frontend files; `./mvnw -q compile` on staged Java | S | med |
| B4 | Gitleaks + secret-scanning GitHub Action already present? Add baseline and rotation policy doc | S | high |
| B5 | Vite bundle-size budgets (`rollup-plugin-visualizer` + threshold CI check) | S | low |
| B6 | Lighthouse CI job that runs against `npm run preview` build, asserts a11y and performance ceilings | M | med |
| B7 | Changelog automation: Conventional Commits + `release-please` action to cut versioned releases | M | low |
| B8 | Document every public API endpoint with `springdoc-openapi`, serve `/v3/api-docs` + Swagger UI at `/api/docs` | M | high |
| B9 | Type-safe API client generated from OpenAPI spec (eliminates hand-written `*.api.ts` drift) | L | high |

---

## Track C - Architecture & scalability

| # | Item | Effort | Impact |
|---|------|--------|--------|
| C1 | Replace direct service-to-service calls with `ApplicationEventPublisher` for cross-cutting events (transaction saved -> holding updated, bill paid -> notification fired). Keeps services testable and decoupled | L | high |
| C2 | Spring Cache + Caffeine for hot reads (asset list, user settings, category lookup) with explicit invalidation on writes | M | high |
| C3 | Caching layer upgrade to Redis once multi-instance deploy is on the table (already have Redis for sessions) | M | med |
| C4 | Generalized rate limiter: annotation + interceptor that accepts buckets per endpoint, replaces per-feature one-offs | M | med |
| C5 | Extract price-client adapters behind a `PriceSource` interface so new markets can be added without touching `PriceSyncService` | M | med |
| C6 | Move scheduled jobs from `@Scheduled` to Quartz so they survive restarts, support clustering, and appear in Grafana | L | med |
| C7 | Read-model split for analytics/dashboard: materialized views or denormalised tables updated by events (less pressure on transactional tables) | L | high |
| C8 | Feature flags (Togglz or in-house): ship new features dark, dogfood, flip to users | M | med |
| C9 | Multi-tenant abstraction layer (ownerId -> tenantId) so even if multi-user stays out of scope, the model supports read-only sharing later | M | low |

---

## Track D - Security hardening

| # | Item | Effort | Impact |
|---|------|--------|--------|
| D1 | Full HTTP security-header review: CSP (report-only first), HSTS preload, Referrer-Policy, Permissions-Policy. Add a test that curls `/` and asserts headers | M | high |
| D2 | Argon2id or scrypt password hashing migration from BCrypt (BCrypt is fine, but Argon2 is state of the art and Spring Security 6 has a wired `Argon2PasswordEncoder`) | S | med |
| D3 | TOTP recovery codes (one-time use, 10 codes shown once on enrollment) - current impl has no lockout escape | M | high |
| D4 | WebAuthn / passkeys as alternative sign-in, same `users` table with an `authenticators` child | L | high |
| D5 | Rate-limit password-reset, email-verification, and refresh endpoints the same way `LoginRateLimiter` covers login | S | high |
| D6 | Session fingerprint binding: store a hash of user-agent + IP-prefix on refresh token rows, reject refresh if it drifts more than N | M | med |
| D7 | Audit log retention policy + automatic redaction of PII after configured window (GDPR-aligned) | M | med |
| D8 | Signed URL scheme for receipt downloads (short-lived HMAC token instead of cookie-authenticated endpoint) | S | low |
| D9 | OWASP Dependency Check (or Renovate's vulnerability report) in CI; fail PRs on new `CRITICAL` CVEs | S | high |
| D10 | Replay-attack protection on refresh-token rotation: mark every consumed refresh token as revoked immediately, even if it was also leaked | S | high |

---

## Track E - Observability

| # | Item | Effort | Impact |
|---|------|--------|--------|
| E1 | OpenTelemetry trace export (OTLP) to Tempo/Grafana; instrument controllers, service boundaries, and external HTTP clients | M | high |
| E2 | Self-hosted Sentry or GlitchTip for exception aggregation with release tagging | M | med |
| E3 | SLI/SLO dashboard: request latency p95, error rate, price-sync freshness; alert on budget burn | M | high |
| E4 | Anomaly detection on `audit_log`: unusual login ip, 5+ failed TOTP in 10m, abnormal transaction amounts | L | med |
| E5 | Structured log schema contract documented in `docs/LOGS.md` (which fields are guaranteed, their types) | S | low |

---

## Track F - Performance

| # | Item | Effort | Impact |
|---|------|--------|--------|
| F1 | Hibernate statistics audit to find N+1 queries; add `@EntityGraph` or `join fetch` where proven hot | M | high |
| F2 | Postgres `EXPLAIN ANALYZE` pass on the 20 slowest queries (pg_stat_statements); add targeted indexes via Flyway | M | high |
| F3 | Virtualize the transaction list once rows exceed ~1000 (`@tanstack/react-virtual`) | S | med |
| F4 | Service worker strategy refinement: stale-while-revalidate for asset icons, network-first for API | M | med |
| F5 | Preload critical routes via `<link rel="modulepreload">` on login completion | S | low |
| F6 | Compress WebSocket frames over STOMP (permessage-deflate extension) | S | low |

---

## Track G - New user-facing features (TR-focused)

These are net-new, shipped under feature flag per B8.

| # | Item | Effort | Impact |
|---|------|--------|--------|
| G1 | **Tax helper**: annual dividend stoppage (stopaj) aggregate per year, capital gains threshold warning for the new TR withholding thresholds | M | high |
| G2 | **Bank account tracking**: `accounts` entity with balance + multi-currency; transactions link to an account; emergency-fund calculator rolls off it | L | high |
| G3 | **Bank CSV import**: Garanti/Is Bankasi/Akbank CSV parsers behind the same preview+commit flow as `ExcelImportService` | L | high |
| G4 | **Portfolio comparison**: side-by-side value/P&L chart across selected portfolios and date range | M | med |
| G5 | **Asset correlation matrix**: heatmap of daily-return correlations between owned assets (helps with concentration risk) | M | med |
| G6 | **Monte Carlo net-worth projection**: 10k-iteration simulation with configurable mean/stdev per asset class | L | med |
| G7 | **Debt snowball vs avalanche comparator**: given current debts, show total interest paid under each strategy with a slider for extra monthly payment | S | med |
| G8 | **Crypto wallet read-only**: connect an Ethereum/Solana address, balance appears as a synthetic holding (Etherscan / Solscan public APIs) | L | low |
| G9 | **DRIP simulator**: model dividend reinvestment vs cash over the portfolio horizon | S | low |
| G10 | **Investor journal**: free-text notes linked to a portfolio / transaction / date (thesis tracking, rebalance rationale) | S | med |
| G11 | **Emergency-fund coverage**: "you can survive N months at current avg expense"; red/amber/green tile on dashboard | S | high |
| G12 | **Rebalance executor**: turn the existing drift view into a "buy X of ASSET_A, sell Y of ASSET_B" suggestion, one-click materialise to transactions | M | high |
| G13 | **Scenario planner for expenses**: sliders for monthly categories, project budget + savings rate change | M | med |
| G14 | **Physical cash tracking**: vault-style entity for cash positions (gold under mattress, envelope budget), shows up in net worth | S | low |
| G15 | **Voice transaction capture (mobile)**: Web Speech API on mobile PWA to dictate "40 lira market expense" | M | low |
| G16 | **ML auto-categorisation**: upgrade the rule engine with a local trained classifier (nlp.js or similar client-side) over historical descriptions | L | low |

---

## Track H - Documentation & onboarding

| # | Item | Effort | Impact |
|---|------|--------|--------|
| H1 | `docs/ARCHITECTURE.md` refresh: sequence diagrams for auth, price-sync, backup/restore | S | med |
| H2 | `docs/OPERATIONS.md`: how to backup, rotate VAPID keys, bump schema, recover from a bad migration | S | high |
| H3 | `docs/THREAT_MODEL.md`: STRIDE over the auth and data layers, explicit trust boundaries | M | med |
| H4 | Screencast or `docs/QUICK_START.md` for new user from login -> first portfolio -> first bill | S | low |
| H5 | Public demo instance (seeded read-only data) for the README | M | low |

---

## Won't do

Explicitly out of scope for this project. Don't revisit without a good reason.

- Multi-user / family sharing (single-owner design)
- Native mobile apps (PWA covers mobile)
- TimescaleDB migration (plain Postgres fine at single-user scale)
- Crypto custody / hot wallet key storage (read-only only)
- Broker order execution (data in, orders out is outside the security posture we're willing to maintain)
- Full accounting ledger (double-entry) - single-user finance tracking is deliberately simpler

---

## Suggested phasing

If I had to ship these in order, I'd do:

1. **Phase 23 - Coverage completion**: A1, A2, A5, A6, B8 (WebMvcTest + DataJpaTest + JaCoCo gate + OpenAPI spec). One week. Sets the floor everything else builds on.
2. **Phase 24 - Security hardening**: D1, D3, D5, D9, D10. Small + high impact.
3. **Phase 25 - Architecture cleanup**: C1, C2. Event-driven + caching. Unblocks C7.
4. **Phase 26 - Observability**: E1, E2, E3. Essential before any ambitious feature work.
5. **Phase 27 - Tax & accounts**: G1 + G2 + G3. Highest user impact among features.
6. **Phase 28 - Rebalance + emergency fund**: G11 + G12. Natural follow-on to the portfolio track.
7. **Phase 29 - Portfolio analytics**: G4 + G5 + G6. Monte Carlo lands after correlation matrix since it reuses the return series.
8. **Phase 30 - Performance & polish**: F1, F2, F3.

Tracks B, H, and the remaining G items slot opportunistically between phases.
