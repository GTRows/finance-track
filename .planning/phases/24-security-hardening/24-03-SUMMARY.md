---
phase: 24-security-hardening
plan: 03
subsystem: auth
tags: [webauthn, passkey, assertion, frontend, react-query, audit]

requires:
  - phase: 24-security-hardening
    provides: WebAuthnRegistrationService.start/finish, AuthenticatorRepository owner-scoped queries, WebAuthnConfig beans, WEBAUTHN_REGISTER_* audit constants
provides:
  - WebAuthnAssertionService.start (decoy-aware, prevents username enumeration) + finish (clone detection on sign_count, TOTP gating, AuthResponse parity with password login)
  - WebAuthnAssertionController exposing public POST /api/v1/auth/webauthn/login/start and /finish
  - AuthenticatorController exposing authenticated GET /api/v1/auth/webauthn/authenticators and DELETE /{id}
  - WEBAUTHN_LOGIN, WEBAUTHN_LOGIN_FAILED, WEBAUTHN_CLONE_DETECTED, WEBAUTHN_AUTHENTICATOR_REVOKED audit constants
  - frontend webauthnApi axios module + base64url helpers + React Query hooks (useWebAuthnRegister/Login/useAuthenticators/useDeleteAuthenticator)
  - PasskeySection in the security settings (enrol with name, list, revoke) + WebAuthnLoginButton on the login page
  - en/tr i18n strings for auth.passkey* and settings.passkey*
affects: [24-04, 24-07]

tech-stack:
  added: []
  patterns:
    - "Decoy Redis entry on assertion start for unknown usernames to remove the timing-based enumeration oracle"
    - "Sign-count strict-monotonicity check on assertion finish; failure deletes the authenticator and audits WEBAUTHN_CLONE_DETECTED"
    - "Frontend ArrayBuffer <-> base64url at the WebAuthn JSON boundary; raw buffers never leave the hook"

key-files:
  created:
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnAssertionService.java
    - backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnAssertionController.java
    - backend/src/main/java/com/fintrack/auth/webauthn/AuthenticatorController.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/AllowedCredential.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionStartRequest.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionStartResponse.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionFinishRequest.java
    - backend/src/main/java/com/fintrack/auth/webauthn/dto/AuthenticatorResponse.java
    - backend/src/test/java/com/fintrack/auth/webauthn/WebAuthnAssertionServiceTest.java
    - backend/src/test/java/com/fintrack/auth/webauthn/AuthenticatorControllerWebMvcTest.java
    - frontend/src/utils/base64url.ts
    - frontend/src/utils/base64url.test.ts
    - frontend/src/api/webauthn.api.ts
    - frontend/src/hooks/useWebAuthn.ts
    - frontend/src/components/settings/PasskeySection.tsx
    - frontend/src/components/auth/WebAuthnLoginButton.tsx
  modified:
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/java/com/fintrack/common/config/SecurityConfig.java
    - frontend/openapi.json
    - frontend/src/api/openapi.types.ts
    - frontend/src/pages/LoginPage.tsx
    - frontend/src/pages/SettingsPage.tsx
    - frontend/src/i18n/locales/en.json
    - frontend/src/i18n/locales/tr.json

key-decisions:
  - "D4 (assertion + UI): mirror AuthService.login response shape exactly so the existing frontend completeAuth path handles passkey logins identically to password logins, including the TOTP challengeToken gate."
  - "Decoy Redis entry on unknown-username start (sentinel value DECOY_USER_ID) instead of skipping the Redis write; same Redis call shape, same TTL, no timing-leak signal."
  - "PasskeySection lives next to TotpSection in the existing security card on SettingsPage rather than introducing a new ProfilePage; this is an owner-only app, the settings page already houses every other security control."

issues-created: [ISS-110]

duration: ~70 min
completed: 2026-05-04
---

# Phase 24 Plan 03: WebAuthn Assertion + Frontend Integration Summary

**Passkeys are end-to-end live: enrol from settings, sign in from the login page, with sign-count clone detection and the TOTP gate preserved on every assertion.**

## Performance

- **Duration:** ~70 min (across two execution attempts after a rate-limit interruption mid-run)
- **Started:** 2026-05-04T14:04:04Z
- **Completed:** 2026-05-04T17:20:00Z
- **Tasks:** 2 of 3 shipped, 1 deferred to ISS-110
- **Files created:** 16
- **Files modified:** 8
- **New tests:** base64url round-trip suite (4 cases), WebAuthnAssertionServiceTest (5 cases including decoy/clone/TOTP branches), AuthenticatorControllerWebMvcTest (2 cases)

## Accomplishments

- Backend assertion ceremony fully functional: `start(username)` issues a 32-byte SecureRandom challenge under `webauthn:assert:<base64url(challenge)>` (5-min TTL) and stores either the userId (known) or a `DECOY_USER_ID` sentinel (unknown) so the response shape and Redis cost are identical for present and absent accounts.
- `finish(body)` extracts the challenge from clientDataJSON, atomically pops the Redis entry with `getAndDelete`, rejects decoy/missing entries, deserialises the stored CBOR-encoded COSE key, runs `WebAuthnManager.parse + verify`, enforces strict sign-count monotonicity, and on regression deletes the authenticator + audits `WEBAUTHN_CLONE_DETECTED` before throwing `BusinessRuleException("Authenticator integrity check failed", "WEBAUTHN_CLONE_DETECTED")`.
- TOTP gate preserved: when the resolved user has TOTP enabled, the response carries `challengeToken` (not access/refresh tokens) and audits `TOTP_CHALLENGE_ISSUED` — the existing frontend `completeAuth(response)` path then routes through the established TOTP form without any new code.
- AuthResponse parity with `AuthService.login` via a private `buildAuthResponse(user)` helper that calls `JwtUtil.generateAccessToken` + `RefreshTokenService.createRefreshToken(userId, RequestContext.userAgent(), RequestContext.clientIp())` exactly as the password path does.
- `SecurityConfig.PUBLIC_PATHS` extended with only the two assertion endpoints; registration (`/register/**`) and management (`/authenticators/**`) remain authenticated.
- `AuditAction` gains `WEBAUTHN_LOGIN`, `WEBAUTHN_LOGIN_FAILED`, `WEBAUTHN_CLONE_DETECTED`, `WEBAUTHN_AUTHENTICATOR_REVOKED`.
- `AuthenticatorController` provides authenticated `GET /authenticators` (owner-scoped via `findByUserIdOrderByCreatedAtDesc`) and `DELETE /{id}` (owner-scoped via `deleteByIdAndUserId`); deletion audits `WEBAUTHN_AUTHENTICATOR_REVOKED` with `id=<uuid>` in the detail.
- Frontend `base64url.ts` provides the only ArrayBuffer / base64url crossings; raw buffers never leak past `useWebAuthn.ts`. Round-trip + 32-byte challenge + unpadded encoding tests pin the shape (4 cases, all green under Vitest).
- `webauthn.api.ts` types match the backend's flat DTOs (`rpId`/`rpName`/`userId`/`userName` for register-start, `AllowedCredential` array for assertion-start) — confirmed by reading `RegistrationStartResponse.java` and `AssertionStartResponse.java` after a first attempt assumed the nested PublicKeyCredential JSON shape and was rewritten.
- React Query hooks: `useWebAuthnRegister` decodes options, calls `navigator.credentials.create`, encodes the attestation, and invalidates the authenticator list on success; `useWebAuthnLogin` does the symmetric assertion call and returns the backend `AuthResponse`; `useAuthenticators` fetches the list (gated on `isWebAuthnSupported`); `useDeleteAuthenticator` revokes by id and invalidates the list cache.
- `PasskeySection` (in the existing security card on `SettingsPage`) shows an "Add passkey" form (name input, enroll button, NotAllowedError UX), then a list of enrolled keys with name + creation/last-used timestamps + per-row revoke. Empty state and unsupported-browser state both rendered.
- `WebAuthnLoginButton` (below the password form on `LoginPage`) reads the username via the existing form state, disables until a username is typed, and on success calls the same `completeAuth(response)` the password form uses — so TOTP-gated users are routed to the existing TOTP code-entry form transparently.
- en/tr i18n strings added for every new UI string under `auth.passkey*` and `settings.passkey*`. The `auth.orDivider` key adds the visual "or" between password and passkey on the login page.

## Task Commits

1. **Task 1: Backend assertion service + controllers + audit + WebMvc tests + OpenAPI regen** — `c988168` (feat)
2. **Task 2: Frontend webauthn API + hooks + UI integration + i18n** — `7bdcb65` (feat)

**Plan metadata:** committed alongside this SUMMARY (docs).

## Files Created/Modified

Created (backend):

- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnAssertionService.java` — start/finish ceremony, decoy + clone-detection + TOTP-gating logic
- `backend/src/main/java/com/fintrack/auth/webauthn/WebAuthnAssertionController.java` — public `/login/start`, `/login/finish` REST endpoints
- `backend/src/main/java/com/fintrack/auth/webauthn/AuthenticatorController.java` — authenticated GET / + DELETE /{id}
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/AllowedCredential.java`
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionStartRequest.java`
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionStartResponse.java`
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/AssertionFinishRequest.java`
- `backend/src/main/java/com/fintrack/auth/webauthn/dto/AuthenticatorResponse.java`
- `backend/src/test/java/com/fintrack/auth/webauthn/WebAuthnAssertionServiceTest.java` — 5 cases (known/unknown start, happy-path finish, clone detection, TOTP gate)
- `backend/src/test/java/com/fintrack/auth/webauthn/AuthenticatorControllerWebMvcTest.java` — list + revoke

Created (frontend):

- `frontend/src/utils/base64url.ts` + `frontend/src/utils/base64url.test.ts`
- `frontend/src/api/webauthn.api.ts` — six-method axios module
- `frontend/src/hooks/useWebAuthn.ts` — register/login/list/delete hooks + isWebAuthnSupported
- `frontend/src/components/settings/PasskeySection.tsx` — settings UI
- `frontend/src/components/auth/WebAuthnLoginButton.tsx` — login UI

Modified:

- `backend/src/main/java/com/fintrack/audit/AuditAction.java` — four new constants
- `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java` — PUBLIC_PATHS appends `/api/v1/auth/webauthn/login/{start,finish}`
- `frontend/openapi.json` + `frontend/src/api/openapi.types.ts` — regenerated to include the five new endpoints + DTOs
- `frontend/src/pages/LoginPage.tsx` — passkey button + divider below password form
- `frontend/src/pages/SettingsPage.tsx` — PasskeySection slotted next to TotpSection in the security card
- `frontend/src/i18n/locales/en.json` + `frontend/src/i18n/locales/tr.json` — 19 new keys total

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Unknown-username start behaviour | Decoy Redis entry with sentinel `DECOY_USER_ID` value | Same Redis round-trip cost as the known path; `finish` rejects decoys via `DECOY_USER_ID.equals(redisValue)` so the authenticator never gets through. Plan permitted both decoy and no-write — picked decoy because the response/timing shape is uniform. |
| TOTP interaction with passkey login | Passkeys do NOT bypass TOTP | A passkey is a single-factor authenticator, not an MFA replacement. If a user has TOTP on, the assertion finish returns `challengeToken` (no tokens) and the frontend routes to the TOTP form. Mirrors `AuthService.login` exactly. |
| Where the passkey UI lives | `SettingsPage` security card alongside TOTP/password/sessions | The plan referenced "ProfilePage" but no such page exists in this brownfield repo; SettingsPage is the established home for every security control. |
| Frontend types for register-start | Flat shape (`rpId`/`rpName`/`userId`/`userName`) | Initial draft assumed a nested `rp.{id,name}` and `user.{id,name,displayName}` JSON shape; corrected after reading `RegistrationStartResponse.java` to match what the backend actually emits. |

## Deviations from Plan

### Auto-fixed during execution

**1. [Rule 1 - Bug] Frontend webauthn.api.ts type mismatch with backend DTOs**

- **Found during:** Task 2 (frontend integration), after writing the first draft based on the plan's pseudocode.
- **Issue:** First draft of `webauthn.api.ts` typed register-start with nested `rp` and `user` objects, mirroring the standard PublicKeyCredentialCreationOptions shape. The backend's `RegistrationStartResponse.java` is a flat record (`rpId`, `rpName`, `userId`, `userName`); a flat-to-nested mismatch would have made enrol fail at runtime as soon as the user clicked "Add passkey".
- **Fix:** Rewrote the TypeScript shape to match the backend record exactly; `useWebAuthnRegister` reshapes to the nested browser API just before calling `navigator.credentials.create`.
- **Files modified:** `frontend/src/api/webauthn.api.ts`, `frontend/src/hooks/useWebAuthn.ts`.
- **Verification:** Captured by `npm run typecheck` (now green) and the existing OpenAPI `git diff --exit-code` gate.
- **Committed in:** 7bdcb65 (Task 2 commit)

### Deferred Enhancements

Logged to `.planning/ISSUES.md` for future consideration:

- **ISS-110** — Full WebAuthn ceremony E2E test using `com.webauthn4j.test.client.ClientPlatform`. Discovered while executing plan 24-03 Task 3. Building a real cryptographic round-trip requires adding `webauthn4j-test` as a test-scope dependency and roughly 200-300 LOC of fixture wiring (EC keypair, CBOR attestation synthesis, signed clientData round-trip). Deferred because Task 1's Mockito service test already exercises every branch (happy/clone/TOTP/decoy) and the existing `OpenApiSpecGeneratorTest` boot path catches Spring-level routing regressions; the remaining gap is true cryptographic regression safety, which is valuable but not blocking for shipping passkeys end-to-end.

---

**Total deviations:** 1 auto-fixed (Rule 1 bug, type mismatch corrected before commit), 1 deferred (ISS-110).
**Impact on plan:** Tasks 1 and 2 fully ship the user-facing capability — a user can enrol a passkey from settings and sign in from the login page today. Task 3's deferral leaves a regression-safety gap that is logged and will be picked up via `/gsd:consider-issues` between phases.

## Issues Encountered

- The previous execution attempt produced Task 1 staged but uncommitted before hitting an upstream rate limit; the resumed run audited the staged code, ran the targeted test class to confirm green (clone detection and TOTP branches both fired in the log output), and committed Task 1 as `c988168`. No code changes were needed during the audit.

## Authentication Gates

None — no third-party CLI or service required interactive auth during this plan.

## Next Phase Readiness

- D4 milestone closed end-to-end across plans 24-02 (registration foundation) and 24-03 (assertion + UI). A user can today enrol a passkey from `/settings` and sign in from `/login`.
- WebAuthn properties remain at dev defaults (`rpId=localhost`, `origin=http://localhost:5173`); plan 24-07 (D9) will fail-fast on blank `WEBAUTHN_RPID` / `WEBAUTHN_ORIGIN` in the prod profile.
- Sign-count clone detection only fires when an authenticator reports a non-zero counter; some platform authenticators (Apple Touch ID, Windows Hello) deliberately keep `signCount=0`. The current implementation correctly skips the strict-monotonicity check when `newSignCount == 0`, so platform authenticators are not falsely flagged.
- Refresh-token issuance during assertion finish reuses `RefreshTokenService.createRefreshToken(userId, userAgent, clientIp)` — when plan 24-04 (D6 fingerprint binding) lands, the passkey path automatically inherits the new fingerprint behaviour with no further changes here.

Ready for `24-04-PLAN.md` (D6 refresh-token session fingerprint binding).

---
*Phase: 24-security-hardening*
*Completed: 2026-05-04*
