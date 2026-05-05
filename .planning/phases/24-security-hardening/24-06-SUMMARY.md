---
phase: 24-security-hardening
plan: 06
subsystem: budget
tags: [receipts, signed-url, hmac, sha256, security, react-query]

requires:
  - phase: 23-coverage-completion
    provides: Receipt OCR pipeline persisting receipts under per-user dir; ReceiptStorageService.load() boundary
  - phase: 24-security-hardening
    provides: Spring Security 6 filter chain conventions established by JwtAuthFilter (24-01..24-04)

provides:
  - ReceiptUrlSigner stateless HMAC-SHA256 signer with @PostConstruct secret-length guard
  - ReceiptSigningProperties bound from fintrack.receipt.{signing-secret,token-ttl}
  - GET /api/v1/budget/transactions/{id}/receipt/url returning {url, expiresAt}
  - GET /api/v1/budget/transactions/{id}/receipt accepts ?token= alternative to JWT
  - SignedReceiptTokenFilter scaffolds anonymous auth before JwtAuthFilter when ?token= present
  - useReceiptUrl React Query hook (4-min refetch inside 5-min server TTL)
  - ReceiptThumbnail component for direct <img src> rendering

affects:
  - 24-07 (D9 production-fail-fast): RECEIPT_SIGNING_SECRET added to the env vars that must be non-blank in prod
  - Future budget UI: thumbnails can render inline without Blob round-trips

tech-stack:
  added: []
  patterns:
    - "HMAC-SHA256 stateless signed URLs (token = base64url(userId:txnId:expiry:hexMac))"
    - "Synthetic-auth filter scaffolds anonymous Authentication so anyRequest().authenticated() passes; cryptographic gate at controller boundary"
    - "Constant-time MAC comparison via MessageDigest.isEqual to defeat timing oracle"

key-files:
  created:
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptUrlSigner.java
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptSigningProperties.java
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptUrlResponse.java
    - backend/src/main/java/com/fintrack/auth/SignedReceiptTokenFilter.java
    - backend/src/test/java/com/fintrack/budget/receipt/ReceiptUrlSignerTest.java
    - frontend/src/hooks/useReceiptUrl.ts
    - frontend/src/components/budget/ReceiptThumbnail.tsx
    - frontend/src/components/budget/ReceiptThumbnail.test.tsx
  modified:
    - backend/src/main/java/com/fintrack/FinTrackApplication.java
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptController.java
    - backend/src/main/java/com/fintrack/common/config/SecurityConfig.java
    - backend/src/test/java/com/fintrack/budget/receipt/ReceiptControllerWebMvcTest.java
    - backend/src/main/resources/application.yml
    - frontend/openapi.json
    - frontend/src/api/openapi.types.ts
    - frontend/src/api/receipt.api.ts
    - frontend/src/components/budget/ReceiptAction.tsx
    - frontend/src/pages/LoginPage.test.tsx
    - frontend/src/utils/base64url.ts

key-decisions:
  - "Token format embeds userId, txnId, expiry, and hexMac in a single base64url payload — verifier is fully self-contained (no DB read, no shared TTL state)"
  - "SignedReceiptTokenFilter scaffolds an anonymous Authentication only when method=GET, path matches the receipt regex, and ?token= is present; the cryptographic gate is enforced at the controller, not the filter"
  - "5-minute server TTL with 4-minute frontend refetch keeps the URL fresh while limiting the rotation window"
  - "Added getReceiptUrl method to receipt.api.ts (cohesion with existing receipt module) instead of plan-suggested budget.api.ts"

patterns-established:
  - "Stateless signed URLs at controller boundary, with a permitAll filter scaffold so the global anyRequest().authenticated() rule still gates non-tokenised paths"

issues-created: []

duration: 28 min
completed: 2026-05-05
---

# Phase 24 Plan 06: Signed URL Scheme for Receipts Summary

**Receipts now render directly via short-lived HMAC-SHA256 signed URLs; auth-header download path stays as a fallback.**

## Performance

- **Duration:** ~28 min
- **Started:** 2026-05-05T07:33:02Z
- **Completed:** 2026-05-05T08:01:00Z
- **Tasks:** 3
- **Files modified:** 19 (8 created, 11 modified)

## Accomplishments

- `ReceiptUrlSigner` issues stateless HMAC-SHA256 tokens that embed the owner, transaction, and expiry; verifier is pure and constant-time.
- New authenticated `GET /receipt/url` endpoint and `?token=` query support on the existing download endpoint deliver fully-qualified URLs safe for `<img src>`.
- `SignedReceiptTokenFilter` keeps the global `anyRequest().authenticated()` posture intact; cryptographic verification happens at the controller boundary, not the filter.
- Frontend `useReceiptUrl` React Query hook + `ReceiptThumbnail` component remove the Blob/`URL.createObjectURL` workaround; `ReceiptAction.handleView` now opens the signed URL directly in a new tab.
- OpenAPI spec and TypeScript types regenerated to expose the new surface; frontend contract tests stay green.

## Task Commits

1. **Task 1: ReceiptUrlSigner service + properties + tests** — `4dd199c` (feat)
2. **Task 2: Wire signed URL into ReceiptController and SecurityConfig + WebMvc tests + OpenAPI regen** — `62ed5db` (feat)
3. **Task 3: Frontend useReceiptUrl + ReceiptThumbnail + ReceiptAction integration** — `c3700b7` (feat)
4. **Deviation: 24-03 verify-gate regressions** — `2e0228a` (fix)

**Plan metadata:** pending docs commit

## Files Created/Modified

### Backend
- `backend/src/main/java/com/fintrack/budget/receipt/ReceiptUrlSigner.java` — HMAC-SHA256 signer with secret-length guard
- `backend/src/main/java/com/fintrack/budget/receipt/ReceiptSigningProperties.java` — record bound from `fintrack.receipt.*`
- `backend/src/main/java/com/fintrack/budget/receipt/ReceiptUrlResponse.java` — DTO record `(url, expiresAt)`
- `backend/src/main/java/com/fintrack/budget/receipt/ReceiptController.java` — added `/url` GET and `?token=` handling on `download()`
- `backend/src/main/java/com/fintrack/auth/SignedReceiptTokenFilter.java` — synthetic-auth scaffolding filter
- `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java` — wires the signed-receipt filter immediately before `JwtAuthFilter`
- `backend/src/main/java/com/fintrack/FinTrackApplication.java` — registers `ReceiptSigningProperties`
- `backend/src/main/resources/application.yml` — `fintrack.receipt.signing-secret` and `token-ttl` defaults
- `backend/src/test/java/com/fintrack/budget/receipt/ReceiptUrlSignerTest.java` — 9 unit tests
- `backend/src/test/java/com/fintrack/budget/receipt/ReceiptControllerWebMvcTest.java` — 6 new MVC tests

### Frontend
- `frontend/src/api/receipt.api.ts` — `signedUrl()` method + `SignedReceiptUrl` type
- `frontend/src/hooks/useReceiptUrl.ts` — React Query hook with 4-min refetch
- `frontend/src/components/budget/ReceiptThumbnail.tsx` — renders signed URL via `<img src>`
- `frontend/src/components/budget/ReceiptThumbnail.test.tsx` — vitest coverage
- `frontend/src/components/budget/ReceiptAction.tsx` — view path now opens signed URL directly
- `frontend/openapi.json` — new `/receipt/url` operation + `?token=` parameter
- `frontend/src/api/openapi.types.ts` — regenerated

### Verification gate fixes (deviation)
- `frontend/src/pages/LoginPage.test.tsx` — wraps render in `QueryClientProvider`
- `frontend/src/utils/base64url.ts` — explicit `Uint8Array<ArrayBuffer>` return type

## Decisions Made

- **Token transports the userId.** Plan suggested verifier-extracts-from-token, kept that contract: signer outputs `userId:txnId:expiry:hexMac` and the verifier returns the embedded userId so the controller never has to consult the DB to learn ownership. This keeps the gate stateless.
- **Filter does not validate.** `SignedReceiptTokenFilter` only installs a synthetic anonymous authentication; the actual MAC verification lives in `ReceiptUrlSigner.verifyAndExtractUserId(...)` called from `ReceiptController.download(...)`. Single source of truth for cryptographic checks.
- **`receipt.api.ts` over plan-specified `budget.api.ts`.** The codebase already isolated all receipt HTTP under `receipt.api.ts` (upload/download/remove). Adding `signedUrl` next to its peers is more cohesive than splitting receipt API across two modules.
- **Filter ordering.** Security chain now adds JwtAuthFilter first, then SignedReceiptTokenFilter before it, then AutheliaForwardAuthFilter after. Reverse-order from initial WIP because `addFilterBefore(custom, customClass)` requires `customClass` to already be registered in the chain.
- **No frontend Blob path remains.** `ReceiptAction.handleView` shed both `receiptApi.download` (Blob) and `URL.createObjectURL`/`revokeObjectURL`. The signed URL lives long enough (5 min) for the new tab to load directly, and re-issue is a single React Query call away.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] LoginPage tests crashed on `No QueryClient set`**
- **Found during:** Task 3 verification (`npm test`)
- **Issue:** Plan 24-03 added `WebAuthnLoginButton` (uses `useWebAuthn` React Query hook) to `LoginPage` but did not update `LoginPage.test.tsx` to wrap renders in a `QueryClientProvider`. Five tests had been failing silently because the npm test exit code was masked in earlier runs.
- **Fix:** Use `createWrapper()` from `@/test-utils/queryWrapper` and wrap the `MemoryRouter` tree.
- **Files modified:** `frontend/src/pages/LoginPage.test.tsx`
- **Verification:** `npm test` now reports 231/231 tests passing across 57 files.
- **Committed in:** `2e0228a` (deviation commit)

**2. [Rule 1 - Bug] `useWebAuthn` failed `tsc -b` under TypeScript 5.7+ strict `BufferSource` typing**
- **Found during:** Task 3 verification (`npm run build`)
- **Issue:** `base64UrlToBuffer` returned `Uint8Array<ArrayBufferLike>`. TypeScript 5.7's tightened `BufferSource = ArrayBufferView<ArrayBuffer> | ArrayBuffer` rejects shared-buffer-backed Uint8Arrays at the WebAuthn API surface. `npm run build` failed with 4 TS2322 errors in `useWebAuthn.ts`.
- **Fix:** Allocate the backing buffer explicitly via `new Uint8Array(new ArrayBuffer(binary.length))`; return type tightened to `Uint8Array<ArrayBuffer>`.
- **Files modified:** `frontend/src/utils/base64url.ts`
- **Verification:** `npm run build` exits 0; `base64url` round-trip tests still pass; `useWebAuthn` typechecks without further casts.
- **Committed in:** `2e0228a` (deviation commit)

**3. [Rule 1 - Bug] Filter chain wiring failed: `JwtAuthFilter does not have a registered order`**
- **Found during:** Task 2 verification (`scripts/regen-openapi.sh` boot)
- **Issue:** Initial WIP ordered `addFilterBefore(signedReceiptTokenFilter, JwtAuthFilter.class)` BEFORE the line that adds `JwtAuthFilter` itself. Spring Security requires the reference filter to already be registered in the chain.
- **Fix:** Reorder so `JwtAuthFilter` is registered first, then `SignedReceiptTokenFilter` is inserted before it, then `AutheliaForwardAuthFilter` after.
- **Files modified:** `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java`
- **Verification:** OpenAPI regen now boots cleanly; `mvnw verify` is green.
- **Committed in:** `62ed5db` (Task 2 commit)

### Deferred Enhancements

None — every discovery was a real bug surfaced by the plan's verification gates.

---

**Total deviations:** 3 auto-fixed (3 bugs), 0 deferred
**Impact on plan:** All three were necessary to honour the plan's verification criteria (`mvnw verify` and `npm run lint && typecheck && test && build` must pass). Two were pre-existing 24-03 regressions that the previous plans missed because their gate checks were narrower; one was a wiring mistake in this plan's own WIP. No scope creep.

## Issues Encountered

- The previous session left `.planning/current-agent-id.txt` and a partially-staged Task 2 work. Resumed against the WIP, completed it, and removed the stale agent marker before the metadata commit.

## Next Phase Readiness

- Plan 24-07 (D9: OWASP Dependency Check + production-profile fail-fast) can now flag `RECEIPT_SIGNING_SECRET` as a required-non-blank prod env var alongside `JWT_SECRET` and the Redis password.
- Plan 24-08 (AuditService coverage for portfolio/budget/bill mutations) is unaffected — receipt download is intentionally not audited per ticket.
- No new blockers carried forward.

---
*Phase: 24-security-hardening*
*Completed: 2026-05-05*
