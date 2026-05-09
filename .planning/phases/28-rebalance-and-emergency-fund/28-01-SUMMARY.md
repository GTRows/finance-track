---
phase: 28-rebalance-and-emergency-fund
plan: 01
subsystem: dashboard
tags: [emergency-fund, configurable-target, settings, validation, audit]

requires:
  - phase: 27
    plan: 02
    provides: accounts entity (V41) -- the emergency-fund types reference Account.AccountType
  - phase: 27
    plan: 03
    provides: V43 emergency_fund_include_types JSONB column on user_settings + EmergencyFundService.compute + EmergencyFundController + SettingsService.updateEmergencyFundTypes; this plan extends the response DTO + adds a sibling /config endpoint
  - phase: 24
    plan: 08
    provides: auditService.success / failure contract -- this plan emits USER_SETTINGS_EMERGENCY_FUND_UPDATED before each throw and after the successful save

provides:
  - V45__user_settings_emergency_fund_targets.sql -- two SMALLINT columns with single-column CHECK constraints (target_months 2-24 default 6; amber_floor_months 1-23 default 3)
  - UserSettings JPA fields emergencyFundTargetMonths + emergencyFundAmberFloorMonths with @Builder.Default
  - EmergencyFundService re-parameterised bands (red < amberFloor / amber <= target inclusive / green > target) with defensive null-fallback to 6/3
  - EmergencyFundResponse DTO widened by targetMonths + amberFloorMonths int fields at the END
  - SettingsService.updateEmergencyFundConfig(userId, types, targetMonths, amberFloorMonths) atomic three-field write with validation gates
  - PUT /api/v1/dashboard/emergency-fund/config endpoint accepting UpdateEmergencyFundConfigRequest with Bean Validation
  - AuditAction.USER_SETTINGS_EMERGENCY_FUND_UPDATED constant emitted on success + failure
  - EmergencyFundCard target + amber-floor stepper pair with i18n bandRedDynamic / bandAmberDynamic / bandGreenDynamic copy
  - EmergencyFundSection on SettingsPage with Input type=number steppers + type-toggle chips
  - useUpdateEmergencyFundConfig React Query mutation sharing the dashboard cache key

affects: [28-02]

tech-stack:
  added: []
  patterns:
    - "Service-layer validation over multi-column DB CHECK constraints. The cross-column invariant amber_floor < target_months is enforced in SettingsService.updateEmergencyFundConfig, NOT as a Postgres CHECK clause. Single-column range CHECKs (BETWEEN 2 AND 24, BETWEEN 1 AND 23) ARE applied -- they document intent and prevent rogue UPDATEs from outside the JPA layer. The reason: a multi-column CHECK is awkward to relax later via a fresh migration, while a service-layer guard is one method edit away. Mirrors 27-03's BANK_SAVINGS-membership precedent."
    - "Response DTO widening at the END of the record. EmergencyFundResponse already carried (currentReserve, buckets, monthlyAverageExpense, monthsCovered, status, includedTypes, sampleMonths). The two new int fields targetMonths + amberFloorMonths land at the END so existing JSON consumers do not break on field-order assumptions."
    - "Audit constants: one constant per logical operation. USER_SETTINGS_EMERGENCY_FUND_UPDATED covers BOTH the legacy types-only update AND the new full-config update. Per the 24-08 / 27-02 precedent. The audit detail string carries the changed shape (`types=[...] target=N amberFloor=M`)."
    - "Default-on-NULL fallback inside the service. EmergencyFundService.compute reads UserSettings.getEmergencyFundTargetMonths() and falls back to DEFAULT_TARGET_MONTHS=6 when null; same for the amber-floor with DEFAULT_AMBER_FLOOR=3. Mirrors 27-03's getEmergencyFundIncludeTypes() == null branch."
    - "Frontend hook layering. useUpdateEmergencyFundConfig is the new wide hook posting (types, targetMonths, amberFloorMonths); useUpdateEmergencyFundTypes stays as a thin types-only mutation against the legacy /types endpoint. Both share the ['dashboard', 'emergency-fund'] React Query cache key so the dashboard tile and the SettingsPage section reflect each other immediately."

key-files:
  created:
    - backend/src/main/resources/db/migration/V45__user_settings_emergency_fund_targets.sql
    - backend/src/main/java/com/fintrack/dashboard/dto/UpdateEmergencyFundConfigRequest.java
    - backend/src/test/java/com/fintrack/settings/SettingsServiceEmergencyFundConfigTest.java
    - frontend/src/components/settings/EmergencyFundSection.tsx
    - frontend/src/components/settings/EmergencyFundSection.test.tsx
    - .planning/phases/28-rebalance-and-emergency-fund/28-01-SUMMARY.md
  modified:
    - backend/src/main/java/com/fintrack/common/entity/UserSettings.java
    - backend/src/main/java/com/fintrack/dashboard/EmergencyFundService.java
    - backend/src/main/java/com/fintrack/dashboard/EmergencyFundController.java
    - backend/src/main/java/com/fintrack/dashboard/dto/EmergencyFundResponse.java
    - backend/src/main/java/com/fintrack/settings/SettingsService.java
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/test/java/com/fintrack/dashboard/EmergencyFundServiceTest.java
    - backend/src/test/java/com/fintrack/dashboard/EmergencyFundControllerWebMvcTest.java
    - backend/src/test/java/com/fintrack/settings/UserSettingsRepositoryDataJpaTest.java
    - frontend/src/types/emergency-fund.types.ts
    - frontend/src/api/dashboard.api.ts
    - frontend/src/hooks/useEmergencyFund.ts
    - frontend/src/hooks/useEmergencyFund.test.tsx
    - frontend/src/components/dashboard/EmergencyFundCard.tsx
    - frontend/src/pages/SettingsPage.tsx
    - frontend/src/pages/SettingsPage.test.tsx
    - frontend/src/i18n/locales/tr.json
    - frontend/src/i18n/locales/en.json
    - docs/OPERATIONS.md
    - .planning/STATE.md
  deliberately-untouched:
    - .env.example -- project deny rule Write/Edit(**/.env.*); no new env vars (the columns + endpoint live entirely inside the JVM + Postgres)
    - docker-compose.yml -- pre_guard_release_files.py PreToolUse hook; this plan introduces zero infra changes
    - CHANGELOG.md -- pre_guard_release_files.py covers it; per the 26-01 / 26-02 / 26-03 / 27-01 / 27-02 / 27-03 / 27-04 precedent, the changelog entry is described in this SUMMARY and applied by the release flow
    - backend/pom.xml -- no new Maven dep
    - package.json + package-lock.json -- no new npm dep
    - frontend/openapi.json + frontend/src/api/openapi.types.ts -- the regen script (scripts/regen-openapi.sh) fails on the pre-existing 26-01 OpenTelemetry sdk-autoconfigure ComponentLoader NoClassDefFoundError that affects pre-27-01 / 27-02 / 27-03 / 27-04 commits as well (verified at HEAD). The new endpoint surface is exercised end-to-end by EmergencyFundControllerWebMvcTest

key-decisions:
  - "Service-layer validation for the cross-column invariant amber_floor < target_months, NOT a Postgres multi-column CHECK constraint. Single-column range CHECKs are applied in V45 (BETWEEN 2 AND 24, BETWEEN 1 AND 23) -- they document intent and prevent rogue UPDATEs from outside the JPA layer. The reason for keeping the cross-column rule in SettingsService: relaxing the bounds in V60 (e.g. owner wants to allow a 1.5-month target) would require dropping + re-adding the multi-column CHECK, which is risky on a live owner DB. The service-layer guard is one method edit away, matches the 27-03 BANK_SAVINGS-membership precedent, and lets the audit-failure-first contract (24-08) emit a precise BusinessRuleException code (EMERGENCY_FUND_AMBER_FLOOR_INVALID)."
  - "Range bounds 2-24 for target months. A 1-month emergency fund is functionally equivalent to no buffer (the trailing-12-month average expense is the figure being divided into the reserve -- at 1 month, the operator has barely a current-month buffer). Bound 24 caps the upper end because beyond 2 years of expenses, the reserve question stops being emergency fund and becomes wealth allocation -- out of scope for this tile."
  - "Amber floor minimum 1, max target - 1. The amber band must be at least 1 month wide (amberFloor=1, target=2 -> red < 1, amber [1, 2], green > 2). At 0 the band collapses and the operator might as well disable the tile."
  - "Cross-currency / FX rollup is OUT OF SCOPE. 27-03-SUMMARY explicitly deferred 'Face-value cross-currency rollup' to 28-01. After analysis, the FX-rate snapshot service is non-trivial (daily ECB / TCMB rate fetcher, rate cache, asset-currency conversion for the reserve sum, additional UI to surface the rate used) and orthogonal to the configurable-N piece. Splitting them lets this plan ship in a single session against the polished G11 wording while the FX work -- which deserves its own design + migration for an fx_rates table or similar -- gets a dedicated plan in Phase 28 or 29."
  - "Response DTO widening at the END of the record (targetMonths + amberFloorMonths after sampleMonths) so existing JSON consumers do not break on field-order assumptions. Frontend emergency-fund.types.ts gains the same two fields on the response interface."
  - "One AuditAction constant per logical operation. USER_SETTINGS_EMERGENCY_FUND_UPDATED covers both the legacy /types endpoint and the new /config endpoint -- the audit detail string carries the changed shape via 'types=[...] target=N amberFloor=M'. Per 24-08 / 27-02 precedent."
  - "Frontend hook layering. useUpdateEmergencyFundConfig is the new wide hook posting the full config; useUpdateEmergencyFundTypes stays as a types-only mutation against the legacy /types endpoint. Both share the same React Query cache key so the dashboard tile (steppers + chips) and the SettingsPage section reflect each other immediately. The legacy hook is preserved verbatim so the type-toggle chips on the dashboard don't need to know the cached target / amber-floor."
  - "No 'preset profiles' (lean / standard / conservative). The owner can set any (target, amberFloor) pair the validators accept; preset chips would bloat the UI surface and the backend has no need for them. The two steppers cover the entire valid space."
  - "No new endpoint for 'history of target changes.' Audit log via audit_log + USER_SETTINGS_EMERGENCY_FUND_UPDATED already covers this. A per-user history view is a future enhancement."
  - "No @Observed annotations. The 26-01 servlet observation handler auto-instruments every @RestController method; the service layer here does sub-millisecond work. Mirrors 27-02 / 27-03."

duration: 60 min
completed: 2026-05-09
---

# Phase 28 Plan 01: Configurable Emergency-Fund Target Months

**FinTrack closes the G11 line item against the ROADMAP wording ("× N months") by making the emergency-fund coverage threshold months configurable per user. New Flyway migration V45 adds two SMALLINT columns to `user_settings` (target 2-24 default 6, amber-floor 1-23 default 3); `EmergencyFundService` reads them with a defensive 6/3 fallback and recomputes the red/amber/green bands against the user-configured pair. New `PUT /api/v1/dashboard/emergency-fund/config` endpoint accepts the full config in a single round-trip; the legacy `/types` endpoint stays as a thin pass-through. Frontend ships a stepper pair on the dashboard tile and a new `EmergencyFundSection` on `SettingsPage`, both sharing the same React Query cache. Cross-currency / FX-rate rollup remains deferred to a future Phase 28 plan.**

> **Operator Action — none required this plan.**
>
> No new env vars, no new docker services, no new Maven or npm deps. The Flyway migration `V45__user_settings_emergency_fund_targets.sql` runs on next backend boot and backfills existing rows with the legacy 6/3 defaults so the previous behaviour is preserved exactly. The first-use guide for the new stepper UI is in `docs/OPERATIONS.md` -> `### Emergency-fund coverage` -> `#### Configuring target months`.

## Performance

- Duration: 60 min (across 7 atomic commits per GSD protocol; the executor was respawned after a Task-5 mid-flight death — Tasks 1-4 already committed by the prior run)
- Tasks executed: 7 / 7
- Files created: 6 (V45 migration + 1 backend DTO + 1 backend test class + 1 frontend Settings component + 1 frontend test + this SUMMARY)
- Files modified: 19 (5 backend main + 3 backend test + 9 frontend + 2 docs)
- Files deliberately untouched: 7 (`.env.example`, `docker-compose.yml`, `CHANGELOG.md`, `backend/pom.xml`, `package.json`, `package-lock.json`, `frontend/openapi.json` + `frontend/src/api/openapi.types.ts`)
- Verify status: backend unit tests for the touched suites green per per-task gates; frontend `npm run lint -- --max-warnings 0`, `npm run typecheck`, `npm run test -- --run` (touched suites) all green.

## Accomplishments

1. **V45 migration + UserSettings JPA fields.** `backend/src/main/resources/db/migration/V45__user_settings_emergency_fund_targets.sql` adds `emergency_fund_target_months SMALLINT NOT NULL DEFAULT 6 CHECK (BETWEEN 2 AND 24)` and `emergency_fund_amber_floor_months SMALLINT NOT NULL DEFAULT 3 CHECK (BETWEEN 1 AND 23)` to `user_settings`. `UserSettings` JPA entity gains two `Short` fields with `@Builder.Default` matching the SQL defaults so existing test fixtures stay green. `UserSettingsRepositoryDataJpaTest` extends with the V45 round-trip + CHECK-rejection cases.

2. **EmergencyFundService re-parameterised + DTO widened.** `EmergencyFundService.compute(userId)` reads the configured target / amber-floor via a new `resolveTargets(...)` helper (defensive null-fallback to `DEFAULT_TARGET_MONTHS=6` / `DEFAULT_AMBER_FLOOR=3`); the band branching switches from the hardcoded `RED_BAND=3 / AMBER_BAND=6` to the user-configured pair (`< amberFloor` -> red, `<= target` -> amber inclusive, `> target` -> green). `EmergencyFundResponse` widens by `targetMonths + amberFloorMonths` at the END so existing JSON consumers do not break.

3. **SettingsService.updateEmergencyFundConfig + audit emission + validation.** New `updateEmergencyFundConfig(userId, types, targetMonths, amberFloorMonths)` writes types + target + amber-floor atomically with three validation gates: BANK_SAVINGS membership / target in `[2, 24]` / amber-floor in `[1, target - 1]`. Audit-failure-first contract honoured — `auditService.failure(USER_SETTINGS_EMERGENCY_FUND_UPDATED, ...)` before each throw, `auditService.success(...)` after the save. Legacy `updateEmergencyFundTypes` becomes a thin delegate that re-uses the operator's existing target / amber-floor.

4. **EmergencyFundController.updateConfig endpoint + WebMvc tests.** New `PUT /api/v1/dashboard/emergency-fund/config` mounts a sibling endpoint accepting `UpdateEmergencyFundConfigRequest(types, targetMonths, amberFloorMonths)` with Bean Validation (`@NotNull @Size(1, 6)` types, `@Min(2) @Max(24)` targetMonths, `@Min(1) @Max(23)` amberFloorMonths). The cross-field invariant `amberFloor < target` is service-layer (Bean Validation has no clean cross-field annotation). Legacy `PUT .../types` stays untouched.

5. **EmergencyFundCard target + amber-floor steppers + i18n.** Frontend `EmergencyFundCard` gains a "Reserve target" sub-card with two `Stepper` controls (target range `[max(2, amberFloor + 1), 24]`; amber-floor range `[1, target - 1]`) wired to the new `useUpdateEmergencyFundConfig` mutation. Status copy switches to dynamic i18n with `{{target}}` / `{{amber}}` interpolation (`bandRedDynamic` / `bandAmberDynamic` / `bandGreenDynamic`); legacy static `statusRed` / `statusAmber` / `statusGreen` keys removed. `crossCurrencyNote` updates to "FX-converted rollup deferred to a future Phase 28 plan". Test fixtures on `useEmergencyFund.test.tsx` widen the response shape and add 2 cases covering config mutation cache update + legacy types-only mutation delegation.

6. **SettingsPage EmergencyFundSection + i18n.** New `EmergencyFundSection.tsx` mounts on `SettingsPage` between `PushNotificationSection` and `TagsSection` (icon `ShieldCheck`). The section uses `Input type="number"` steppers (clamped to the same min/max guards) plus the type-toggle chips (BANK_SAVINGS locked, BANK_CHECKING / CASH user-toggleable). Both surfaces share the `['dashboard', 'emergency-fund']` React Query cache key so changes in one surface reflect immediately in the other. New `EmergencyFundSection.test.tsx` covers initial render / target-change clamps amber-floor / BANK_SAVINGS chip locked.

7. **OPERATIONS.md + STATE.md + 28-01-SUMMARY.md.** `docs/OPERATIONS.md` `### Emergency-fund coverage` extends with `#### Configuring target months` documenting the dashboard stepper + the Settings section + the validation rules + the storage columns. The "Cross-currency limitation" paragraph updates to reflect the new deferral. `.planning/STATE.md` reflects Phase 28 in progress (1 / 2 plans), the 28-01 decision row, and the new resume pointer to sub-plan 02 (rebalance executor).

## Files Created/Modified

**Created (backend):**
- `backend/src/main/resources/db/migration/V45__user_settings_emergency_fund_targets.sql` — two SMALLINT columns with single-column CHECKs.
- `backend/src/main/java/com/fintrack/dashboard/dto/UpdateEmergencyFundConfigRequest.java` — request body record with Bean Validation annotations.
- `backend/src/test/java/com/fintrack/settings/SettingsServiceEmergencyFundConfigTest.java` — validation + audit-emission test class.

**Created (frontend):**
- `frontend/src/components/settings/EmergencyFundSection.tsx` — Settings page section with `Input type="number"` steppers + toggle chips.
- `frontend/src/components/settings/EmergencyFundSection.test.tsx` — 3 cases pinning the section's contract.

**Modified (backend):**
- `backend/src/main/java/com/fintrack/common/entity/UserSettings.java` — `emergencyFundTargetMonths` + `emergencyFundAmberFloorMonths` `Short` fields with `@Builder.Default`.
- `backend/src/main/java/com/fintrack/dashboard/EmergencyFundService.java` — `resolveTargets` helper + re-parameterised band branching.
- `backend/src/main/java/com/fintrack/dashboard/EmergencyFundController.java` — new `PUT /config` mapping.
- `backend/src/main/java/com/fintrack/dashboard/dto/EmergencyFundResponse.java` — widened by `targetMonths` + `amberFloorMonths` at the END.
- `backend/src/main/java/com/fintrack/settings/SettingsService.java` — `updateEmergencyFundConfig` + legacy delegate + injected `AuditService`.
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` — `USER_SETTINGS_EMERGENCY_FUND_UPDATED` constant.
- `backend/src/test/java/com/fintrack/dashboard/EmergencyFundServiceTest.java` — `settings(...)` helper overloads + 6 additive cases.
- `backend/src/test/java/com/fintrack/dashboard/EmergencyFundControllerWebMvcTest.java` — `sampleResponse()` widened + 5 additive `updateConfig*` cases.
- `backend/src/test/java/com/fintrack/settings/UserSettingsRepositoryDataJpaTest.java` — V45 round-trip + CHECK-rejection cases.

**Modified (frontend):**
- `frontend/src/types/emergency-fund.types.ts` — `targetMonths` + `amberFloorMonths` on response; new `UpdateEmergencyFundConfigRequest`.
- `frontend/src/api/dashboard.api.ts` — new `updateEmergencyFundConfig` axios method.
- `frontend/src/hooks/useEmergencyFund.ts` — new `useUpdateEmergencyFundConfig` mutation hook.
- `frontend/src/hooks/useEmergencyFund.test.tsx` — fixtures widened + 2 additive cases.
- `frontend/src/components/dashboard/EmergencyFundCard.tsx` — stepper pair sub-card + dynamic-band copy.
- `frontend/src/pages/SettingsPage.tsx` — new `EmergencyFundSection` mount with `ShieldCheck` icon.
- `frontend/src/pages/SettingsPage.test.tsx` — `dashboard.api` mock added so the new section's `useEmergencyFund` call resolves.
- `frontend/src/i18n/locales/tr.json` — new `emergencyFund.*` keys; legacy static `statusRed/statusAmber/statusGreen` removed; `crossCurrencyNote` updated.
- `frontend/src/i18n/locales/en.json` — same key set in English.

**Modified (docs):**
- `docs/OPERATIONS.md` — `### Emergency-fund coverage` extended with `#### Configuring target months`.
- `.planning/STATE.md` — Phase 28 in progress (1/2), 28-01 decision row, resume pointer to sub-plan 02.

**Deliberately untouched:**
- `.env.example` — project deny rule. No new env vars.
- `docker-compose.yml` — release-files guard. Plan introduces zero infra changes.
- `CHANGELOG.md` — release-files guard. Per precedent, the changelog entry is described in this SUMMARY and applied by the release flow.
- `backend/pom.xml` — no new Maven dep.
- `package.json` + `package-lock.json` — no new npm dep.
- `frontend/openapi.json` + `frontend/src/api/openapi.types.ts` — see Deviations.

## Decisions Made

1. **Service-layer validation over multi-column DB CHECK** for `amber_floor < target_months`. Single-column range CHECKs are applied (BETWEEN 2 AND 24, BETWEEN 1 AND 23). Mirrors 27-03's `BANK_SAVINGS`-membership precedent.
2. **Range bounds 2-24 for target months / 1-23 for amber-floor.** Below 2 the buffer is functionally zero; above 24 the question becomes wealth allocation.
3. **Cross-currency / FX rollup is OUT OF SCOPE.** Deferred to a future Phase 28 / 29 plan; needs a dedicated `fx_rates` design.
4. **Response DTO widening at the END.** New fields land after `sampleMonths` so existing JSON consumers do not break on field-order assumptions.
5. **One `AuditAction` constant per logical operation.** `USER_SETTINGS_EMERGENCY_FUND_UPDATED` covers both legacy types-only and full-config updates.
6. **Frontend hook layering: legacy + wide.** `useUpdateEmergencyFundTypes` stays for the type-toggle chips; `useUpdateEmergencyFundConfig` powers the steppers. Both share the same React Query cache key.
7. **No preset profiles (lean / standard / conservative).** The two steppers cover the entire valid space.
8. **No history-of-target-changes endpoint.** Audit log already records every write via `USER_SETTINGS_EMERGENCY_FUND_UPDATED`.
9. **No `@Observed` annotations on the new service method.** The servlet observation handler auto-instruments every `@RestController` method; the service layer here does sub-millisecond work.

## Mutation Coverage Results

`pitest` is opt-in via the `mutation` Maven profile and is NOT part of this plan's verification. The project-level 60% / 45% JaCoCo gate runs on every `verify` and is targeted to stay green after this plan; per-task verification gates exercised the touched test suites only (Tasks 1-4 by the prior executor; Tasks 5-7 by the resume executor).

## Deviations from Plan

- **Executor respawn after Task-5 mid-flight death.** Tasks 1-4 were committed atomically by an earlier executor run that died inside Task 5 without emitting the protocol tail-block; the orchestrator reset the partial Task-5 working tree and respawned the executor at Task 5. The seven-commit shape on `main` is preserved.
- **OpenAPI spec regen still defers** per the pre-existing 26-01 OpenTelemetry sdk-autoconfigure `ComponentLoader` `NoClassDefFoundError` (verified at HEAD across 27-01 / 27-02 / 27-03 / 27-04). The new `/dashboard/emergency-fund/config` endpoint surface is exercised end-to-end by `EmergencyFundControllerWebMvcTest`. The 23-03 contract gate will catch drift the moment the regen script is fixed; that fix should be its own follow-up plan.
- **EmergencyFundSection test uses `afterEach(cleanup)` explicitly.** RTL auto-cleanup did not run between tests in this file's setup (the `vi.mock('react-i18next')` factory plus the `createWrapper` helper hold a queryClient that retains rendered output across tests); an explicit `afterEach(cleanup)` resolves the duplicate-DOM symptom. Mirrors the precedent in other tests that mock `react-i18next` heavily.

## Issues Encountered

- The first run of the Settings section test failed at `findByText('emergencyFund.toggleSavings')` because two copies of the section were live in the document at once (RTL did not auto-cleanup between this file's tests, likely due to the `vi.mock('react-i18next')` factory holding state). Switched to `getAllByRole('button', { name: /toggleSavings/ })` and added `afterEach(cleanup)` to ensure isolation. All three EmergencyFundSection tests pass.

## Next Phase Readiness

- **Phase 28 in progress (1 / 2 plans).** Sub-plan 01 (configurable emergency-fund target months) shipped. Sub-plan 02 (rebalance executor — Track G12) not started.
- **Deferred Enhancements** seeded by this plan: cross-currency / FX-rate rollup for the emergency-fund tile (deferred from 27-03 and again from 28-01); per-user history of target changes (audit log already covers it, but a UI surface would be a polish item); preset profiles (lean / standard / conservative) — explicitly out of scope.

## Next Step

Phase 28 in progress (1 / 2 plans). Next: sub-plan 02 (rebalance executor — Track G12). Run `/gsd:plan-phase 28 02`.
