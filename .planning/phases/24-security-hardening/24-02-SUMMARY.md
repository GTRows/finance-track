---
phase: 24-security-hardening
plan: 02
subsystem: auth
tags: [webauthn, passkey, registration, flyway, redis, webauthn4j, audit]

requires:
  - phase: 24-security-hardening
    provides: AuditAction constants pattern, AuditService.success/failure pattern, BusinessRuleException error-code convention
provides:
  - V38 authenticators table (FK to users.id ON DELETE CASCADE, unique credential_id, user_id index)
  - Authenticator JPA entity in com.fintrack.common.entity
  - AuthenticatorRepository (findByUserIdOrderByCreatedAtDesc / findByCredentialId / findByIdAndUserId / deleteByIdAndUserId)
  - WebAuthnProperties bound to fintrack.webauthn.{rpId,rpName,origin}
  - WebAuthnConfig publishing WebAuthnManager + ObjectConverter beans
  - WebAuthnRegistrationService.start (32-byte SecureRandom challenge, 5-minute Redis TTL, getAndDelete replay protection)
  - WebAuthnRegistrationService.finish (attestation parse + verify, COSE serialisation, AAGUID extraction)
  - POST /api/v1/auth/webauthn/register/start and /finish (authenticated, NOT in PUBLIC_PATHS)
  - WEBAUTHN_REGISTER_{STARTED,COMPLETED,FAILED} audit actions
affects: [24-03, 24-07]

tech-stack:
  added: ["com.webauthn4j:webauthn4j-core 0.27.0"]
  patterns:
    - "Redis-backed WebAuthn challenge with getAndDelete() for atomic replay protection"
    - "Owner-scoped authenticator queries via findByIdAndUserId / deleteByIdAndUserId"
    - "ObjectConverter shared between ceremony parsing and COSE-key persistence"

key-files:
  created:
    - backend/src/main/resources/db/migration/V38__add_authenticators.sql
    - backend/src/main/java/com/fintrack/common/entity/Authenticator.java
    - backend/src/main/java/com/fintrack/auth/webauthn/AuthenticatorRepository.java
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnConfig.java
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnProperties.java
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnRegistrationService.java
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnRegistrationController.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/PubKeyCredParam.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/RegistrationStartResponse.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/RegistrationFinishRequest.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/RegistrationFinishResponse.java
    - backend/src/test/java/com/fintrack/auth/webauthn/WebAuthnRegistrationServiceTest.java
    - backend/src/test/java/com/fintrack/auth/webauthn/AuthenticatorRepositoryDataJpaTest.java
  modified:
    - backend/pom.xml
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/resources/application.yml
    - frontend/openapi.json
    - frontend/src/api/openapi.types.ts

key-decisions:
  - "D2 (library): webauthn4j-core 0.27.0 over yubico:webauthn-server-core. Spring affinity, smaller surface, idiomatic records/Optionals; Apache 2.0 vs BSD-2 keeps the licensing block uniform."

patterns-established:
  - "WebAuthn challenge lifecycle: SecureRandom(32) -> base64url -> Redis 5-min TTL -> getAndDelete on finish to prevent replay"
  - "WEBAUTHN_REGISTER_* audit naming mirrors TOTP_* and PASSWORD_* constants in AuditAction"
  - "Sub-package com.fintrack.auth.webauthn isolates passkey ceremony surface from existing TOTP and refresh-token code"

issues-created: []

duration: ~50 min
completed: 2026-05-04
---

# Phase 24 Plan 02: WebAuthn Foundation + Registration Ceremony

**Backend can now enrol passkeys: webauthn4j-core 0.27.0 wired, V38 authenticators table live, registration start/finish endpoints exposed under /api/v1/auth/webauthn/register/.**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-05-04T15:30:00Z
- **Completed:** 2026-05-04T16:14:00Z
- **Tasks:** 3 (1 decision checkpoint resolved + 2 implementation)
- **Files modified:** 18 (13 created, 5 modified)
- **New tests:** 2 classes / 8 cases (4 service mock + 4 repository @DataJpaTest)
- **Backend verify:** 917 tests, 0 failures, JaCoCo + Spotless gates pass.

## Accomplishments

- Library decision (D2) closed: webauthn4j-core 0.27.0 added to backend/pom.xml. The `webauthn4j.WebAuthnManager` and `ObjectConverter` are exposed as beans from a dedicated `WebAuthnConfig`, so both the ceremony parser and the COSE-key serialiser share a single converter instance.
- Flyway V38 introduces the `authenticators` table — UUID PK, FK to `users.id` on cascade delete (mirrors the `refresh_tokens` revocation model), `BYTEA` `credential_id` (unique) + `public_key_cose`, `BIGINT sign_count`, plus optional `attestation_fmt`, `aaguid`, `transports`, `name`, and create/last-used timestamps.
- JPA layer in place: `Authenticator` entity in `com.fintrack.common.entity` and `AuthenticatorRepository` in the new `com.fintrack.auth.webauthn` package, with the four owner-scoped finders/deleter the rest of phase 24 will need.
- Registration ceremony shipped end-to-end on the backend:
  - `start(userId)` issues a 32-byte SecureRandom challenge, base64url-encodes it, stores it under `webauthn:reg:<userId>` in Redis with a 5-minute TTL, and returns the full `PublicKeyCredentialCreationOptions` payload (rpId, rpName, user handle as base64url(uuid-bytes), pubKeyCredParams ES256+RS256, timeout=60s, attestation=none, residentKey=preferred, userVerification=preferred).
  - `finish(userId, body)` pops the challenge atomically with `getAndDelete()` (no second use possible), parses + verifies the attestation through webauthn4j, extracts credentialId / AAGUID / COSE public key (CBOR-serialised), and persists the row. Library failures map to `BusinessRuleException("WEBAUTHN_REGISTRATION_INVALID")`; missing/expired challenges map to `BusinessRuleException("WEBAUTHN_CHALLENGE_INVALID")`. Both branches audit `WEBAUTHN_REGISTER_FAILED`.
- `WebAuthnRegistrationController` exposes `POST /start` and `POST /finish` under `/api/v1/auth/webauthn/register`. Endpoints stay outside `PUBLIC_PATHS` so the JWT filter authenticates the caller before the ceremony runs.
- AuditAction gains `WEBAUTHN_REGISTER_STARTED` / `_COMPLETED` / `_FAILED` and the service emits the right one on every branch.
- OpenAPI artefacts regenerated: `frontend/openapi.json` picks up both new endpoints and their DTOs; `frontend/src/api/openapi.types.ts` is in sync; the existing `src/api/__contract__/api-contract.test.ts` (24 cases) still passes.

## Task Commits

1. **Task 2: Library + V38 + entity + repository** — `d1ecf3b` (feat)
2. **Task 3: Properties + service + controller + DTOs + tests + OpenAPI regen** — `a9caaf3` (feat)

**Plan metadata:** committed alongside this SUMMARY (docs).

## Files Created/Modified

Created:

- `backend/src/main/resources/db/migration/V38__add_authenticators.sql` — authenticators table + per-user index
- `backend/src/main/java/com/fintrack/common/entity/Authenticator.java` — JPA entity (BYTEA credential_id/public_key_cose, lombok builders, CreationTimestamp)
- `backend/src/main/java/com/fintrack/auth/webauthn/AuthenticatorRepository.java` — owner-scoped queries
- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnConfig.java` — Spring bean wiring for WebAuthnManager + ObjectConverter
- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnProperties.java` — `@ConfigurationProperties(prefix = "fintrack.webauthn")`
- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnRegistrationService.java` — start + finish ceremony
- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnRegistrationController.java` — REST endpoints
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/{PubKeyCredParam,RegistrationStartResponse,RegistrationFinishRequest,RegistrationFinishResponse}.java` — record DTOs
- `backend/src/test/java/com/fintrack/auth/webauthn/WebAuthnRegistrationServiceTest.java` — Mockito unit tests (4 cases)
- `backend/src/test/java/com/fintrack/auth/webauthn/AuthenticatorRepositoryDataJpaTest.java` — Testcontainers @DataJpaTest (4 cases)

Modified:

- `backend/pom.xml` — adds `com.webauthn4j:webauthn4j-core 0.27.0`
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` — three new constants
- `backend/src/main/resources/application.yml` — `fintrack.webauthn.{rpId,rpName,origin}` block (env-overridable, dev defaults)
- `frontend/openapi.json` — regenerated to include the two new endpoints + DTOs
- `frontend/src/api/openapi.types.ts` — regenerated TypeScript surface

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| D2 — WebAuthn library | `com.webauthn4j:webauthn4j-core 0.27.0` | Spring affinity (companion `webauthn4j-spring-security` exists for later filter integration), smaller surface, idiomatic Java records and Optionals, Apache 2.0 license keeps the dependency block uniform with the rest of the stack. The yubico path is deferred — its main draw (mature attestation coverage) is not needed for the FinTrack passkey UX, which only registers user-presence credentials. |

## Deviations from Plan

None — plan executed exactly as written.

The `<resume-signal>` for the D2 checkpoint allowed a yolo-mode default; no human input was required and no architectural deviations surfaced during implementation. Library API matched the plan's pseudocode (only adjustment: `WebAuthnManager.verify(...)` is the 0.27.x method name, not `validate(...)` from older versions; the test and service are aligned).

## Issues Encountered

- Repository @DataJpaTest skipped on this run because Testcontainers' npipe-based Docker auto-detection fails on Windows + Docker Desktop. The test class is correctly guarded with `@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")`, matching the project-wide pattern from plan 23-01. The OpenAPI regeneration script side-steps the same npipe limitation by starting an ephemeral Postgres directly via the `docker` CLI; the regen ran end-to-end in 22 seconds and committed both `frontend/openapi.json` and `openapi.types.ts`.

## Next Phase Readiness

- D4 backend ceremony is complete; assertion (login) ceremony, list/revoke endpoints, and the React/UI integration land in `24-03-PLAN.md`.
- WebAuthn properties default to dev values (rpId=localhost, origin=http://localhost:5173). Plan 24-07 (D9) will add the prod-profile fail-fast for `WEBAUTHN_RPID` / `WEBAUTHN_ORIGIN` blanks alongside CORS, Redis-password, and JWT/receipt-secret guards.
- AuditService coverage for the new actions is in line with the existing TOTP_* and PASSWORD_* constants — no follow-up to AuditService itself.
- ISSUES.md unchanged: no enhancements needed deferral. The existing ISS-100..109 backlog from plan 23-02 is unaffected.

Ready for `24-03-PLAN.md` (D4 WebAuthn assertion ceremony + frontend integration).

---
*Phase: 24-security-hardening*
*Completed: 2026-05-04*
