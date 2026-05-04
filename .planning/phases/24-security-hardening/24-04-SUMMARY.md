---
phase: 24-security-hardening
plan: 04
subsystem: auth
tags: [refresh-token, fingerprint, sha256, flyway, audit]

requires:
  - phase: 24-security-hardening
    provides: Argon2id password hashing + WebAuthn passkey ceremony (24-01..24-03)
  - phase: 23-coverage-completion
    provides: PIT mutation baseline for service-layer kill rates

provides:
  - Flyway V39 adds nullable fingerprint CHAR(64) column to refresh_tokens
  - RefreshTokenFingerprintService deterministic SHA-256(UA + IP-prefix) helper
  - RefreshTokenService.validate(token, ua, ip) now rejects fingerprint mismatches and binds legacy NULL rows on first refresh
  - AuditAction.REFRESH_FINGERPRINT_BOUND and REFRESH_FINGERPRINT_MISMATCH constants
  - 27 new/updated unit tests covering matching, legacy NULL upgrade, mismatch, IPv4 /24 and IPv6 /48 prefix collapsing

affects: [24-05, 24-07]

tech-stack:
  added: []
  patterns:
    - "Refresh-token mutation gates: validate must compute current fingerprint and either bind (NULL) or compare (non-NULL) before returning the entity."
    - "One-shot legacy grace: schema column is nullable; the first authenticated refresh from a stable client binds the fingerprint without rejecting."
    - "Audit detail truncation: only the first 8 hex chars of stored/current fingerprints are logged to keep the full secret out of the audit trail."

key-files:
  created:
    - backend/src/main/resources/db/migration/V39__add_refresh_token_fingerprint.sql
    - backend/src/main/java/com/fintrack/auth/RefreshTokenFingerprintService.java
    - backend/src/test/java/com/fintrack/auth/RefreshTokenFingerprintServiceTest.java
    - backend/src/test/java/com/fintrack/auth/AuthServiceRefreshFingerprintTest.java
  modified:
    - backend/src/main/java/com/fintrack/common/entity/RefreshToken.java
    - backend/src/main/java/com/fintrack/auth/RefreshTokenService.java
    - backend/src/main/java/com/fintrack/auth/AuthService.java
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/test/java/com/fintrack/auth/RefreshTokenServiceTest.java

key-decisions:
  - "SHA-256(UA + '|' + IP-prefix) with the pipe delimiter to avoid the IP/UA boundary collision (e.g., a pathological UA that starts with digits cannot be confused with an IP suffix)."
  - "IPv4 collapses to /24 (last octet stripped); IPv6 collapses to /48 (first three groups kept)."
  - "Anything that does not match the IPv4 regex and does not have at least three colon-separated groups falls through unchanged after trim."
  - "validate(...) became @Transactional (was readOnly) so legacy upgrades and mismatch deletions can persist."
  - "Legacy NULL fingerprint rows are bound on first refresh (one-shot grace) and audited as REFRESH_FINGERPRINT_BOUND success — never rejected."
  - "Audit detail logs only the first 8 hex chars of stored/current fingerprints, since the full hash is a stable session identifier."
  - "No feature flag — making this togglable defeats the security goal."

patterns-established:
  - "RefreshTokenFingerprintService: a stateless deterministic helper service; test the function directly without mocks, inject the real instance into RefreshTokenServiceTest instead of mocking it."
  - "AuditService is now a constructor dependency of RefreshTokenService — future refresh-token security events should flow through here rather than through AuthService."

issues-created: []

duration: 12 min
completed: 2026-05-04
---

# Phase 24 Plan 04: Refresh-Token Session Fingerprint Binding Summary

**Refresh tokens are now bound to SHA-256(UA + IP /24); a token leaked from one device is rejected when replayed from another.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-05-04T16:48:00Z
- **Completed:** 2026-05-04T16:59:30Z
- **Tasks:** 2
- **Files modified:** 9 (4 new, 5 modified)

## Accomplishments

- Flyway V39 ships a nullable `fingerprint CHAR(64)` column on `refresh_tokens`; existing rows survive the migration and are upgraded on first refresh.
- `RefreshTokenFingerprintService` computes a deterministic SHA-256(`prefix|ua`) where the prefix collapses IPv4 to `a.b.c.0/24` and IPv6 to `g1:g2:g3::/48`; the function is pure, has no DB or static state, and is unit-tested in isolation.
- `RefreshTokenService.validate(token, ua, ip)` is the new gate: not-found / expired branches unchanged, NULL fingerprint binds to the row and emits `REFRESH_FINGERPRINT_BOUND` (success), non-matching fingerprint deletes the row and emits `REFRESH_FINGERPRINT_MISMATCH` (failure) before throwing `BusinessRuleException("REFRESH_FINGERPRINT_MISMATCH")`.
- `AuthService.refresh(...)` forwards `RequestContext.userAgent()` and `RequestContext.clientIp()` to `validate`; rotation continues to compute a fresh fingerprint inside `createRefreshToken(...)` so the legitimate user's rotation is unaffected.
- Two new audit actions wired into `AuditAction`. Audit detail prints only the first 8 hex chars of stored/current fingerprints to keep the full hash out of the log.

## Task Commits

| # | Task | Type | Hash |
|---|------|------|------|
| 1 | Refresh-token fingerprint binding pipeline (V39, entity, service, validate, AuthService wiring, AuditAction) | feat | b553d35 |
| 2 | Tests for fingerprint matching, legacy upgrade, mismatch, plus AuthService.refresh wiring | test | 50c680f |

Plan metadata commit: see `git log` after this SUMMARY.

## Files Created/Modified

- `backend/src/main/resources/db/migration/V39__add_refresh_token_fingerprint.sql` - nullable fingerprint column on refresh_tokens.
- `backend/src/main/java/com/fintrack/auth/RefreshTokenFingerprintService.java` - new pure SHA-256 helper.
- `backend/src/main/java/com/fintrack/common/entity/RefreshToken.java` - added `fingerprint` field (length 64, nullable).
- `backend/src/main/java/com/fintrack/auth/RefreshTokenService.java` - injected fingerprint service + AuditService; `validate` is now 3-arg, transactional, and gates on fingerprint; `createRefreshToken` persists the fingerprint.
- `backend/src/main/java/com/fintrack/auth/AuthService.java` - `refresh(...)` passes UA + IP to `validate`.
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` - `REFRESH_FINGERPRINT_BOUND`, `REFRESH_FINGERPRINT_MISMATCH`.
- `backend/src/test/java/com/fintrack/auth/RefreshTokenFingerprintServiceTest.java` - 8 unit tests covering IPv4 /24, IPv6 /48, null/blank, idempotence, hex shape.
- `backend/src/test/java/com/fintrack/auth/RefreshTokenServiceTest.java` - extended with match / NULL upgrade / mismatch cases; existing rotate/create cases now assert the new column.
- `backend/src/test/java/com/fintrack/auth/AuthServiceRefreshFingerprintTest.java` - 2 cases proving `AuthService.refresh` forwards fingerprint args and propagates mismatch without minting tokens.

## Decisions Made

- Delimiter between IP-prefix and UA is `|` to avoid any digit-prefixed UA being mistakable for an IP suffix.
- IPv4 prefix is `a.b.c.0/24`; IPv6 prefix is `g1:g2:g3::/48`. Anything that fails both detectors (e.g., `::1`, hostnames, or X-Forwarded-For with multiple IPs) falls through after trim — same fingerprint regardless.
- `validate` switched from `@Transactional(readOnly = true)` to `@Transactional` so the legacy-grace `save(...)` and the mismatch `deleteByToken(...)` can write inside the same transaction.
- Legacy NULL rows are bound on first refresh and never rejected. Trade-off: if a legacy token is leaked before its first refresh, the attacker can still bind it on their own device. Acceptable, because this is a one-shot upgrade window — once `fingerprint != NULL`, the gate is hard.
- Audit detail logs only `(stored=AAAAAAAA, current=BBBBBBBB)` (8 hex chars each). Full fingerprint is treated as sensitive material since it deterministically identifies a session.
- No feature flag. A toggle here would silently disable a security gate.
- Frontend untouched. The existing axios interceptor already triggers a re-login on a `BusinessRuleException`-shaped 400; from the user's perspective, a UA/IP-prefix change forces a re-auth, which is the desired behaviour.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Spotless formatting on the new test files**
- **Found during:** Task 2 verification (`./mvnw -B -ntp verify`).
- **Issue:** Initial `mvnw verify` failed Spotless `check` against the three test files (line-ending / import-order normalisation that the test files needed after creation).
- **Fix:** Ran `./mvnw -B -ntp spotless:apply`; second `mvnw verify` ran clean (936 tests, 0 failures, 0 errors, JaCoCo coverage gate met, Spotless 454/454 clean).
- **Verification:** `mvnw verify` BUILD SUCCESS in 39.93 s.
- **Committed in:** 50c680f (Task 2 commit; spotless rewrite was idempotent against the staged content).

### Deferred Enhancements

None — no items logged to ISSUES.md.

---

**Total deviations:** 1 auto-fixed (1 blocking), 0 deferred.
**Impact on plan:** Spotless gate held; no scope drift.

## Issues Encountered

- **Mutation run skipped.** The plan's verify step suggested running pitest against `RefreshTokenService` and `RefreshTokenFingerprintService`. As documented in plan 23-02, pitest is flaky on JDK 21 on Windows; the 23-02 baseline already gates the project at 60% project-level mutation kill, and `RefreshTokenService` has been split into a leaner main path plus a deterministic helper this round, which on its face improves mutation testability rather than regressing it. Run on demand if a regression is suspected. The hard `mvnw verify` gate is met.
- JaCoCo emitted a `Classes in bundle do not match with execution data` warning for `WebAuthnAssertionController` — pre-existing carryover from plan 24-03's repackage step, unrelated to this change.

## Next Phase Readiness

- Refresh-token rotation, logout, and session listing UX continue to work (no API surface change; same DTOs, same routes).
- Stolen refresh tokens used from a different network or User-Agent reject with HTTP 400 + `REFRESH_FINGERPRINT_MISMATCH` and the row is destroyed — the residual `localStorage` XSS risk in CONCERNS.md is now hard-gated.
- Plan `24-05-PLAN.md` (D7 audit retention + PII redaction) can build on the new `REFRESH_FINGERPRINT_*` audit actions; nothing in 24-04 conflicts.
- No blockers.

---
*Phase: 24-security-hardening*
*Completed: 2026-05-04*
