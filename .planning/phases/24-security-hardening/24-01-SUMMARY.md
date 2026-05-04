---
phase: 24-security-hardening
plan: 01
subsystem: auth
tags: [argon2id, bcrypt, spring-security, password-hashing, flyway, delegating-encoder]

requires:
  - phase: 23-coverage-completion
    provides: >
      23-02: opt-in pitest-maven mutation profile with 60% project-level gate and per-class
      baseline (AuthService at 28% — the regression floor for this plan).
      23-03: OpenAPI spec committed and CI-gated; any DTO change forces regen — encoder swap
      is internal so no regen required.

provides:
  - Argon2id default password encoder via DelegatingPasswordEncoder (v5_8 params)
  - Legacy BCrypt hash routing via {bcrypt} prefix map entry + setDefaultPasswordEncoderForMatches fallback
  - V37__prefix_legacy_bcrypt_passwords.sql — idempotent UPDATE prepending {bcrypt} to bare $2_$ rows
  - Rehash-on-login in AuthService.login() committing in the same @Transactional boundary
  - PASSWORD_REHASHED constant in AuditAction; audit emitted on every successful rehash
  - SecurityConfigPasswordEncoderTest — 5 unit tests for encoder contract
  - AuthServiceRehashTest — 3 unit tests for rehash-on-login scenarios

affects:
  - phase: 24-04
    note: refresh-token fingerprint touches the login path; rehash fires before buildAuthResponse so the order is stable
  - phase: 24-07
    note: CI dependency check covers bcprov-jdk18on (BouncyCastle) OWASP vuln gate
  - phase: 24-08
    note: audit coverage plan builds on PASSWORD_REHASHED action already emitted from login path

tech-stack:
  added:
    - bcprov-jdk18on 1.78.1
  patterns:
    - DelegatingPasswordEncoder with explicit id map — default id drives new encodes; legacy ids route verification
    - upgradeEncoding() as the single gate for rehash decisions; no home-grown prefix check

key-files:
  created:
    - backend/src/main/resources/db/migration/V37__prefix_legacy_bcrypt_passwords.sql
    - backend/src/test/java/com/fintrack/common/config/SecurityConfigPasswordEncoderTest.java
    - backend/src/test/java/com/fintrack/auth/AuthServiceRehashTest.java
  modified:
    - backend/pom.xml
    - backend/src/main/java/com/fintrack/common/config/SecurityConfig.java
    - backend/src/main/java/com/fintrack/auth/AuthService.java
    - backend/src/main/java/com/fintrack/audit/AuditAction.java

key-decisions:
  - "Use defaultsForSpringSecurity_v5_8 (not v5_2) for stronger Argon2 memory/iteration params per OWASP 2023"
  - "Prefix-migrate legacy rows via V37 so steady state does not depend on setDefaultPasswordEncoderForMatches; fallback covers only the rollout window"
  - "Rehash inside login transaction (not @Async) so the next login immediately sees the upgraded hash"
  - "upgradeEncoding() is the authoritative rehash gate — no custom prefix string comparison"

patterns-established:
  - "Encoder swap pattern: DelegatingPasswordEncoder + V37 prefix migration + upgradeEncoding rehash hook"

issues-created: []

duration: 45m
completed: 2026-05-04
---

# Phase 24 Plan 01: Argon2id Password Hashing Migration

**Argon2id becomes the default password encoder via DelegatingPasswordEncoder; legacy BCrypt hashes are prefixed by V37 migration and silently upgraded to Argon2id on next successful login with a PASSWORD_REHASHED audit entry.**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-05-04T15:45:00+03:00
- **Completed:** 2026-05-04T15:55:00+03:00
- **Tasks:** 3 of 3
- **Files modified or created:** 7

## Accomplishments

- Added bcprov-jdk18on 1.78.1 and replaced the BCryptPasswordEncoder bean with a DelegatingPasswordEncoder (default id=argon2, v5_8 params; {bcrypt} entry keeps legacy verification working).
- Created V37__prefix_legacy_bcrypt_passwords.sql: idempotent UPDATE that prepends {bcrypt} to all bare $2_$ rows so DelegatingPasswordEncoder routes them by prefix without relying on the setDefaultPasswordEncoderForMatches fallback in steady state.
- Added rehash-on-login in AuthService.login() — passwordEncoder.upgradeEncoding() gates the rewrite; the new Argon2id hash is saved in the same @Transactional boundary so the next login sees the upgraded hash immediately; PASSWORD_REHASHED audit emitted on every upgrade.
- 8 new tests: 5 in SecurityConfigPasswordEncoderTest covering encode prefix, prefixed+unprefixed BCrypt match, and upgradeEncoding true/false; 3 in AuthServiceRehashTest covering rehash+audit on legacy user, no-op on Argon2 user, and no rehash on failed auth.
- Full verify: 909 tests, 0 failures, JaCoCo gate met, Spotless clean.
- OpenAPI: no drift (encoder swap is internal, no DTO change).
- AuthService mutation kill rate: 36% (above the 28% ISS-101 baseline).

## Task Commits

1. **Task 1: Add BouncyCastle + DelegatingPasswordEncoder + V37 migration** - `9c3d9ed` (feat)
2. **Task 2: Rehash-on-login + PASSWORD_REHASHED audit** - `c9181b8` (feat)
3. **Task 3: SecurityConfigPasswordEncoderTest + AuthServiceRehashTest** - `3b0b34e` (test)

## Files Created/Modified

- `backend/pom.xml` - Added bcprov-jdk18on 1.78.1 dependency
- `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java` - Replaced BCryptPasswordEncoder bean with DelegatingPasswordEncoder
- `backend/src/main/resources/db/migration/V37__prefix_legacy_bcrypt_passwords.sql` - Idempotent prefix migration for legacy BCrypt rows
- `backend/src/main/java/com/fintrack/auth/AuthService.java` - Added upgradeEncoding() rehash block in login()
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` - Added PASSWORD_REHASHED constant
- `backend/src/test/java/com/fintrack/common/config/SecurityConfigPasswordEncoderTest.java` - 5 encoder contract tests
- `backend/src/test/java/com/fintrack/auth/AuthServiceRehashTest.java` - 3 rehash-on-login tests

## Decisions Made

- Used `defaultsForSpringSecurity_v5_8` over v5_2 for stronger Argon2 memory/iteration parameters (OWASP 2023 recommendation).
- Prefix-migrate legacy rows in V37 so steady state does not depend on `setDefaultPasswordEncoderForMatches`; the fallback covers only the rollout window before V37 runs.
- Rehash inside the login transaction (synchronous, not @Async) so a follow-up login immediately sees the upgraded hash without a separate DB read.
- `upgradeEncoding()` is the authoritative rehash gate — no custom prefix string comparison.

## Deviations from Plan

None — plan executed exactly as written. The mutation threshold BUILD FAILURE from the scoped pitest run is expected behaviour (the profile's 60% project threshold fires on the single-class scope); the actual kill rate of 36% exceeds the 28% ISS-101 baseline, satisfying the plan's regression-floor requirement.

## Issues Encountered

- Mutation scoped run (`-DmutationThreshold=0`) still triggered the profile's `<mutationThreshold>60</mutationThreshold>` from pom.xml, causing a BUILD FAILURE despite the override flag. This is the same behaviour noted in 23-02. The actual kill rate (36% > 28% floor) confirms no regression. No fix required; the profile gate is intentionally strict for full runs.
- Manual smoke tests (curl + psql verification) are deferred to user-side acceptance testing as noted in the plan's verification block.

## Next Phase Readiness

Ready for `24-02-PLAN.md` (WebAuthn passkey foundation + registration ceremony). The login path is stable: rehash fires before buildAuthResponse, so the 24-04 refresh-token fingerprint addition will not conflict.

---
*Phase: 24-security-hardening*
*Completed: 2026-05-04*
