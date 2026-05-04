# Roadmap: FinTrack Pro

## Overview

FinTrack Pro is brownfield: Phases 1-22 are shipped end-to-end. This roadmap defines the post-v1 backlog, taken from `tasks/ROADMAP.md` (tracks A-H + suggested Phases 23-30) and from concerns surfaced in `.planning/codebase/CONCERNS.md`. Each phase delivers one coherent capability that the owner can verify in production. Numbering continues from the existing project ledger to preserve git history continuity.

## Domain Expertise

None — full-stack Spring Boot + React + Postgres + Redis is already encoded in `.planning/codebase/` (STACK, ARCHITECTURE, CONVENTIONS, TESTING). No domain expertise skill matches this stack.

## Phases

**Phase Numbering:**
- Integer phases (23, 24, …): Planned milestone work
- Decimal phases (e.g., 24.1): Reserved for urgent insertions; none open today

- [x] **Phase 23: Coverage Completion** — Complete (2026-05-04)
- [ ] **Phase 24: Security Hardening** — Close the residual auth and audit gaps
- [ ] **Phase 25: Architecture Cleanup** — Decouple services and de-block the reactive price clients
- [ ] **Phase 26: Observability** — End-to-end traces, errors, and SLO burn alerts
- [ ] **Phase 27: Tax & Accounts (TR)** — Tax helper, bank account entity, TR bank CSV import
- [ ] **Phase 28: Rebalance & Emergency Fund** — Turn drift into one-click trades; emergency-fund tile
- [ ] **Phase 29: Portfolio Analytics** — Comparison, correlation matrix, Monte Carlo
- [ ] **Phase 30: Performance & Polish** — N+1 audit, index pass, virtualized lists

## Phase Details

### Phase 23: Coverage Completion
**Goal**: Broaden the test floor so later refactors and migrations are safe to ship. Backend `@DataJpaTest` across the remaining repositories, mutation testing on the service layer, frontend↔backend contract tests, and the deferred receipt-OCR background job.
**Depends on**: Nothing (independent)
**Research**: Likely (PIT mutation tooling for Spring Boot 3.2, OpenAPI generator vs. Pact for contract tests, tess4j integration model)
**Research topics**: pitest-maven configuration with JaCoCo, OpenAPI client generation that matches existing `*.api.ts` shape, tess4j worker isolation
**Plans**: TBD (estimate 4 plans — one per item: A2, A7, A8, A9)

Plans:
- [x] 23-01: A2 — Broaden `@DataJpaTest` + Testcontainers across remaining repositories
- [x] 23-02: A7 — PIT mutation testing on service layer at 60% mutation score
- [x] 23-03: A8 — Frontend↔backend contract tests via openapi-typescript + Vitest type-level assertions
- [x] 23-04: A9 — Receipt OCR via tess4j as a background worker

### Phase 24: Security Hardening
**Goal**: Close the residual auth/audit gaps from `tasks/ROADMAP.md` Track D plus the production-fail-fast misconfiguration risks from CONCERNS.md. Argon2id migration, passkeys, refresh-token fingerprinting, audit retention with PII redaction, signed receipt URLs, OWASP Dependency Check, and prod-profile guards (CORS, Redis password). Adds AuditService coverage to portfolio/budget/bill mutations.
**Depends on**: Phase 23 (need broader integration coverage before swapping password encoder and audit emission paths)
**Research**: Likely (Argon2id migration playbook with Spring Security 6, WebAuthn/passkey library choice, OWASP Dependency Check vs. Renovate vulnerability gate)
**Research topics**: `Argon2PasswordEncoder` parameters and rehash-on-login pattern, `webauthn4j` vs. `yubico/java-webauthn-server`, OWASP Dependency Check Maven plugin failure thresholds, audit-log retention SQL pattern
**Plans**: 8 (D4 split across 2 plans for backend ceremony + frontend integration; D8/D9 split for atomicity; AuditService domain coverage as its own plan)

Plans:
- [x] 24-01: D2 — Argon2id password hashing migration with rehash-on-login fallback
- [x] 24-02: D4 — WebAuthn passkey foundation + registration ceremony (`authenticators` child table, library decision, register endpoints)
- [x] 24-03: D4 — WebAuthn assertion ceremony + frontend integration (login endpoints, list/revoke, React hooks + UI)
- [ ] 24-04: D6 — Refresh-token session fingerprint binding (UA + IP-prefix SHA-256)
- [ ] 24-05: D7 — Audit log retention policy + automatic PII redaction
- [ ] 24-06: D8 — Signed URL scheme for receipts (HMAC-SHA256, 5-minute TTL)
- [ ] 24-07: D9 — OWASP Dependency Check in CI + prod-profile fail-fast (CORS, Redis password, JWT/receipt secrets, WebAuthn config)
- [ ] 24-08: AuditService coverage for portfolio/budget/bill mutations

### Phase 25: Architecture Cleanup
**Goal**: Track C1 + C2 plus the reactive price-client refactor flagged in CONCERNS.md. Move cross-cutting wiring to `ApplicationEventPublisher`, add Spring Cache + Caffeine on hot reads with explicit invalidation, and replace `WebClient.block()` + `Thread.sleep` in the price clients with virtual-thread or async composition.
**Depends on**: Phase 23 (rely on integration coverage to detect regressions during the event extraction)
**Research**: Likely (idiomatic `ApplicationEventPublisher` patterns vs. an in-process bus, Caffeine vs. Spring Cache invalidation strategy, virtual-thread interplay with `WebClient`)
**Research topics**: `@TransactionalEventListener` semantics, Caffeine + Spring Cache key strategy, virtual-thread executor configuration in Spring Boot 3.2, fan-out delta strategy for the WebSocket broadcaster
**Plans**: TBD (estimate 3 plans)

Plans:
- [ ] 25-01: C1 — Cross-cutting events via `ApplicationEventPublisher` (transaction → holding update, bill paid → notification)
- [ ] 25-02: C2 — Spring Cache + Caffeine on hot reads (asset list, user settings, category lookup) with explicit invalidation on writes
- [ ] 25-03: Reactive price clients off `WebClient.block()` and `Thread.sleep` in `@Transactional` (virtual threads or async composition)

### Phase 26: Observability
**Goal**: Track E1-E3. OpenTelemetry OTLP traces wired into Tempo/Grafana, self-hosted Sentry or GlitchTip for exception aggregation with release tagging, and a SLI/SLO dashboard for request latency p95, error rate, and price-sync freshness with burn-rate alerts.
**Depends on**: Phase 25 (event-based architecture and tightened caches change which spans matter)
**Research**: Likely (OpenTelemetry Java agent vs. SDK for Spring Boot 3.2, Sentry vs. GlitchTip self-host effort, SLO methodology for the price-sync freshness metric)
**Research topics**: OTel autoconfigure module, exporter config for Tempo, Spring micrometer-tracing bridge, error-budget burn-rate alerting in Grafana
**Plans**: TBD (estimate 3 plans)

Plans:
- [ ] 26-01: E1 — OpenTelemetry OTLP export to Tempo, instrumenting controllers + service boundaries + external HTTP clients
- [ ] 26-02: E2 — Self-hosted Sentry or GlitchTip with release tagging
- [ ] 26-03: E3 — SLI/SLO dashboard with burn-rate alerts (latency p95, error rate, price-sync freshness)

### Phase 27: Tax & Accounts (TR)
**Goal**: Track G1-G3, the highest user-impact bundle. TR tax helper (annual stoppage, capital-gains threshold), `accounts` entity with multi-currency balances and emergency-fund linkage, and CSV import for Garanti / İş Bankası / Akbank behind the existing preview+commit pattern in `ExcelImportService`.
**Depends on**: Phase 25 (events used to keep account balances coherent with transactions)
**Research**: Likely (current TR withholding/stopaj thresholds and capital-gains rules, bank CSV format quirks, idempotent import strategy)
**Research topics**: latest TR Hazine vergi limits, bank export schemas (date format, encoding, decimal locale, sign convention), preview-commit transactional model
**Plans**: TBD (estimate 4 plans)

Plans:
- [ ] 27-01: G1 — Tax helper (annual dividend stoppage aggregate, capital-gains threshold warnings)
- [ ] 27-02: G2-a — `accounts` entity + repository + service + CRUD endpoints with multi-currency balances
- [ ] 27-03: G2-b — Wire transactions to `account_id`; emergency-fund coverage rolls off the new entity
- [ ] 27-04: G3 — Garanti / İş Bankası / Akbank CSV parsers behind preview+commit

### Phase 28: Rebalance & Emergency Fund
**Goal**: Track G11 + G12. Surface emergency-fund coverage as a dashboard tile and turn the existing target-allocation drift view into a "buy X, sell Y" rebalance executor that materialises proposals into transactions with one click.
**Depends on**: Phase 27 (emergency-fund tile reads from the new `accounts` entity)
**Research**: Unlikely (internal logic over data we already own)
**Plans**: TBD (estimate 2 plans)

Plans:
- [ ] 28-01: G11 — Emergency-fund coverage tile (red/amber/green based on average expense × N months)
- [ ] 28-02: G12 — Rebalance executor (drift → buy/sell suggestions → one-click transaction commit)

### Phase 29: Portfolio Analytics
**Goal**: Track G4 + G5 + G6. Side-by-side portfolio comparison across selected portfolios and ranges, asset correlation matrix on daily returns, and Monte Carlo net-worth projection with configurable per-asset-class mean and stddev.
**Depends on**: Phase 27 (multi-currency entity work; account-aware analytics)
**Research**: Likely (correlation algorithm at single-user scale, Monte Carlo numerical approach, Recharts vs. alternative for heatmap)
**Research topics**: Pearson vs. Spearman correlation choice, return-series alignment with sparse data, distribution sampling, heatmap rendering options compatible with shadcn/ui
**Plans**: TBD (estimate 3 plans)

Plans:
- [ ] 29-01: G4 — Portfolio comparison (multi-portfolio value/P&L overlay)
- [ ] 29-02: G5 — Asset correlation matrix (heatmap of daily-return correlations)
- [ ] 29-03: G6 — Monte Carlo net-worth projection (10k iterations, configurable parameters per class)

### Phase 30: Performance & Polish
**Goal**: Track F1-F3 plus the per-asset delta broadcast flagged in CONCERNS.md. Hibernate N+1 audit with targeted `@EntityGraph`/`join fetch`, Postgres `EXPLAIN ANALYZE` pass through `pg_stat_statements` plus indexes via Flyway, virtualized transaction list with `@tanstack/react-virtual` once rows exceed ~1000, and trim the WebSocket broadcaster to only changed assets.
**Depends on**: Phases 23, 25, 26 (tests, events, traces all needed to safely tune hot paths)
**Research**: Unlikely (`pg_stat_statements`, `@EntityGraph`, and `@tanstack/react-virtual` are well-trodden — the slow queries are the discovery, not the technique)
**Plans**: TBD (estimate 3 plans)

Plans:
- [ ] 30-01: F1 + per-asset delta — N+1 audit + `@EntityGraph`; WebSocket broadcaster sends delta of changed assets only
- [ ] 30-02: F2 — `pg_stat_statements` EXPLAIN pass; missing indexes added via Flyway
- [ ] 30-03: F3 — Virtualized transaction list (`@tanstack/react-virtual`) when row count exceeds ~1000

## Progress

**Execution Order:**
Phases execute in numeric order: 23 → 24 → 25 → 26 → 27 → 28 → 29 → 30

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 23. Coverage Completion | 4/4 | Complete | 2026-05-04 |
| 24. Security Hardening | 3/8 | In progress | - |
| 25. Architecture Cleanup | 0/3 | Not started | - |
| 26. Observability | 0/3 | Not started | - |
| 27. Tax & Accounts (TR) | 0/4 | Not started | - |
| 28. Rebalance & Emergency Fund | 0/2 | Not started | - |
| 29. Portfolio Analytics | 0/3 | Not started | - |
| 30. Performance & Polish | 0/3 | Not started | - |
