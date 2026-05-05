---
phase: 24-security-hardening
status: complete
plans: 8
started: 2026-05-04
completed: 2026-05-05
tracks: [D2, D4, D6, D7, D8, D9, audit-domain-coverage]
tags: [argon2id, webauthn, refresh-fingerprint, audit-retention, signed-receipt-url, owasp-dep-check, audit-domain]
---

# Phase 24: Security Hardening — Phase Summary

**Eight plans, all green. Closed every Track D gap (`tasks/ROADMAP.md`) plus the production-fail-fast and audit-domain-coverage concerns from `.planning/codebase/CONCERNS.md`. Backend tests: 1012 passing.**

## Plans

| # | Track | Domain | Hash(es) | Date |
|---|-------|--------|----------|------|
| 24-01 | D2 | Argon2id password hashing migration with rehash-on-login | (see git log) | 2026-05-04 |
| 24-02 | D4-a | WebAuthn passkey foundation + registration ceremony (`authenticators` table, webauthn4j-core 0.27.0, register endpoints) | (see git log) | 2026-05-04 |
| 24-03 | D4-b | WebAuthn assertion ceremony + frontend integration (login endpoints, list/revoke, React hooks + UI) | (see git log) | 2026-05-04 |
| 24-04 | D6 | Refresh-token session fingerprint binding (UA + IP-prefix SHA-256) | (see git log) | 2026-05-04 |
| 24-05 | D7 | Audit log retention policy + automatic PII redaction | d9de6ea, 9a016ac, baf11e0, fac6fec | 2026-05-05 |
| 24-06 | D8 | Signed URL scheme for receipts (HMAC-SHA256, 5-minute TTL) | (see git log) | 2026-05-05 |
| 24-07 | D9 | OWASP Dependency Check via opt-in `security` Maven profile + production-profile fail-fast guard | a536235, 6313021 | 2026-05-05 |
| 24-08 | audit-domain-coverage | AuditService coverage for portfolio / budget / bill mutations | eedcf22, 9d00371, 102a878 | 2026-05-05 |

## Headline Outcomes

### D2 — Argon2id password hashing (24-01)

`Argon2PasswordEncoder` (v5_8 params) is the default encoder via `DelegatingPasswordEncoder`; `setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder(12))` carries the legacy bcrypt rollout. `AuthService.login()` calls `passwordEncoder.upgradeEncoding(...)` and rehashes in the same `@Transactional` boundary, emitting `PASSWORD_REHASHED` audit. `bcprov-jdk18on 1.78.1` added; AuthService mutation kill rate held at 36% (above the 28% ISS-101 floor).

### D4 — WebAuthn passkeys (24-02 + 24-03)

`com.webauthn4j:webauthn4j-core 0.27.0` chosen over yubico (Spring affinity, smaller surface, Apache 2.0 keeps the licensing block uniform). V38 `authenticators` child table with FK ON DELETE CASCADE, BYTEA columns, unique credential_id. Register + assert ceremonies live under `com.fintrack.auth.webauthn`, isolated from the existing TOTP / refresh-token surface. Decoy Redis entries on unknown-username assertion remove the timing oracle; strict sign-count monotonicity deletes cloned authenticators and emits `WEBAUTHN_CLONE_DETECTED`. TOTP gate preserved on the assertion path (returns `challengeToken` when user has TOTP on). Six new `WEBAUTHN_*` audit actions. Frontend: `base64url.ts` round-trip helpers, `webauthn.api.ts`, `useWebAuthn.ts` React Query hooks, `PasskeySection` in security settings, `WebAuthnLoginButton` below the login form. Cryptographic E2E test deferred to ISS-110.

### D6 — Refresh-token fingerprint binding (24-04)

`RefreshTokenFingerprintService` computes deterministic SHA-256(`prefix|ua`) where IPv4 collapses to `a.b.c.0/24` and IPv6 to `g1:g2:g3::/48`; pipe delimiter avoids the IP/UA boundary collision. `RefreshTokenService.validate` widened to `(token, ua, ip)` and made `@Transactional`: NULL fingerprint → bind on first refresh and audit `REFRESH_FINGERPRINT_BOUND` (one-shot grace, never reject); mismatch → `deleteByToken`, audit `REFRESH_FINGERPRINT_MISMATCH` failure with first-8-hex prefixes only, throw `BusinessRuleException("REFRESH_FINGERPRINT_MISMATCH")`. No feature flag (toggling defeats the gate). V40 in 24-05 corrected the `fingerprint` column from CHAR(64) to VARCHAR(64) for Hibernate strict-validation compliance.

### D7 — Audit retention + PII redaction (24-05)

`AuditPiiRedactor` scrubs five PII pattern families (email / JWT / IPv4 / IPv6 / TOTP recovery code) at the single `AuditService.record(...)` boundary before truncating to 500 chars. `AuditRetentionWorker` `@Scheduled(cron = "0 30 3 * * *")` deletes via native Postgres `DELETE WHERE id IN (SELECT id ... ORDER BY id LIMIT :limit)`, chunked at `batchSize` (default 1000), 100-iteration safety cap, NOT `@Transactional` so each batch commits independently. Config bound from `AUDIT_RETENTION_DAYS=90` / `AUDIT_RETENTION_BATCH_SIZE=1000` / `AUDIT_RETENTION_ENABLED=true`. New `GET /api/v1/admin/audit/retention` returns the live config; admin gate is the existing `/api/v1/admin/**` security rule.

### D8 — Signed receipt URLs (24-06)

`ReceiptUrlSigner` issues stateless HMAC-SHA256 tokens (`base64url(userId:txnId:expiry:hexMac)`); verifier is constant-time via `MessageDigest.isEqual`, `@PostConstruct` rejects secrets shorter than 32 bytes. New authenticated `GET /api/v1/budget/transactions/{id}/receipt/url` returns `{url, expiresAt}` (5-minute TTL). The existing receipt endpoint accepts `?token=` alongside JWT; `SignedReceiptTokenFilter` (placed immediately before `JwtAuthFilter`) installs an anonymous synthetic Authentication so the global `authenticated()` rule passes — the cryptographic gate stays at the controller boundary. Frontend: `useReceiptUrl` React Query hook with 4-min refetch inside the 5-min server TTL; `ReceiptThumbnail` renders the signed URL via `<img src>`.

### D9 — OWASP Dependency Check + production fail-fast (24-07)

`org.owasp:dependency-check-maven 11.1.1` lives behind an opt-in `security` Maven profile (sibling of the `mutation` profile from 23-02) with `failBuildOnCVSS=9.0` (CRITICAL only); empty `owasp-suppressions.xml` skeleton ships with the `<notes>` + review-date convention; new CI job `dependency-check` is gated by `dorny/paths-filter@v3` on `pom.xml` / suppression changes and is informational only. `ProductionProfileGuard` `@Component @Profile("production")` runs from `@PostConstruct` and aggregates every misconfiguration into a single `IllegalStateException`: empty/wildcard `CORS_ALLOWED_ORIGINS`, blank `SPRING_REDIS_PASSWORD`, blank or default `JWT_SECRET`, blank or default `RECEIPT_SIGNING_SECRET`, blank or default `WEBAUTHN_RPID`, blank or default `WEBAUTHN_ORIGIN`. `CorsProperties` (`@ConfigurationProperties("fintrack.cors")`) binds `CORS_ALLOWED_ORIGINS`; the wildcard `setAllowedOriginPatterns` fallback is gated to `Environment.matchesProfiles("!production")`.

### Audit Domain Coverage (24-08)

Seven services (PortfolioService, HoldingService, InvestmentTransactionService, BudgetService, CategoryService, BudgetRuleService, BillService) emit `auditService.success(action, userId, username, "id=...")` after every mutating method's DB write and `auditService.failure(...)` before each `BusinessRuleException` throw site. AuditAction grew by 13 constants. Seven new `*ServiceAuditTest` fixtures pin the contract via `Mockito.verify` with `eq` + `contains` matchers. The CONCERNS.md "Domain mutations not audited" line is closed. ISS-111 logged for TagService + AllocationService follow-up.

## Cross-Phase Patterns Established

- **Audit at the storage boundary, never at the call site.** PII redaction (24-05) and per-mutation emission (24-08) both run inside `AuditService.record(...)`; callers never redact, never compose detail strings outside the entity-id contract.
- **Fail-fast at boot, never at request time.** `ProductionProfileGuard` (24-07) and `ReceiptUrlSigner.@PostConstruct` (24-06) both move misconfiguration detection from the request path to startup. The pattern's natural extension — boot-time validation of audit-table presence, scheduler-bean enablement, etc. — is on the table for Phase 26 Observability.
- **Username via SecurityContextHolder, never via DTO.** Audit emission (24-08) and JWT rehash on login (24-01) both pull username from the security context. RequestContext stays jakarta-servlet-only.
- **Per-service ServiceAuditTest fixture.** 24-08 establishes a uniform Mockito.verify shape across seven services; future plans extending audit coverage just add another `*ServiceAuditTest`.

## Issues Logged Across Phase 24

- **ISS-110** — Full WebAuthn ceremony E2E test using `com.webauthn4j.test.client.ClientPlatform` (24-03 follow-up).
- **ISS-111** — Audit emission for TagService and AllocationService (24-08 follow-up).

## Verify-Suite State at Phase Close

- Backend `mvnw verify`: 1012 tests pass, 0 failures, 132 skipped (Testcontainers-bound suites without a Docker daemon — expected). All assertions including JaCoCo coverage thresholds, Spotless format gate, and OpenApiSpecGenerator boot path remain green.
- Frontend `npm test` / `npm run typecheck` / `npm run build`: all green at 24-06 (last frontend-touching plan).
- Project-level mutation kill rate: 23-02 baseline at 63% holds; per-class deltas for 24-08 services are best-effort due to the JDK 21 / Windows pitest flake noted in 24-04.

## Next Phase

Phase 25 — Architecture Cleanup. `/gsd:plan-phase 25`.

---
*Phase opened: 2026-05-04 (24-01)*
*Phase closed: 2026-05-05 (24-08)*
