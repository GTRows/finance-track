---
phase: 27-tax-and-accounts
plan: 01
subsystem: report
tags: [tax, tr, dividend, stoppage, withholding, capital-gains, threshold, yaml-config, single-locale]

requires:
  - phase: 18
    provides: V34 dividends.withholding_tax column -- the read source for the stoppage rollup queries
  - phase: 18
    provides: CapitalGainsService -- TrTaxService consumes compute(userId, year) as a black box and layers threshold banding on top
  - phase: 8
    provides: V8 user_settings.timezone column -- TrTaxService derives the default fiscal year via UserSettings.timezone with Europe/Istanbul fallback
  - phase: 26-observability
    plan: 02
    provides: org.yaml:snakeyaml transitive dependency via sentry-spring-boot-starter-jakarta:7.18.0 -- TrTaxParametersLoader reads tax-parameters-tr.yml without a new pom dependency

provides:
  - TrTaxParametersLoader @Component reading classpath:tax/tax-parameters-tr.yml at startup with graceful fallback on missing/malformed YAML
  - DividendRepository.sumStoppageTotalsByPortfoliosAndRange (gross / withholding / net 3-tuple) and sumStoppageByAssetAndPortfoliosAndRange (per-asset breakdown)
  - TrTaxService threshold banding (under <80% / approaching 80-100% / over >100% / unknown when params missing) + warnings list (approaching-threshold / threshold-exceeded / parameters-missing)
  - GET /api/v1/reports/tax/tr endpoint with optional ?year query and a >=2000 lower bound
  - /reports/tax/tr frontend route + Analytics tile + taxTr.* i18n namespace in tr.json + en.json
  - tax-parameters-tr.yml seeded with 2024 (158,000 TRY) and 2025 (200,000 TRY) listed-equity thresholds sourced from GIB / Resmi Gazete announcements

affects: [27-02, 27-03, 27-04]

tech-stack:
  added:
    - "First use site of the existing org.yaml:snakeyaml:2.2 transitive (no new Maven dep -- 26-02 brings it in via Sentry)"
  patterns:
    - "Classpath-YAML for owner-tunable locale-keyed configuration that mutates yearly without code changes (alternative to Java constants / Flyway migrations / admin-settings table)"
    - "Sibling-locale extension model (tax-parameters-{locale}.yml + XxTaxService) deferred until a second locale lands -- generic TaxParameters interfaces are NOT premature-abstracted"
    - "Status-banding constants (APPROACHING_RATIO 0.80) live at the service layer NOT the YAML so owner edits cannot accidentally change UX bands"
    - "Asset-class scoping of capital-gains threshold via parameters.appliesTo + Asset.AssetType enum-name match (re-fetches each event's asset because CapitalGainsResponse.Event does not carry assetType)"
    - "Year-defaulting via UserSettings.timezone with Europe/Istanbul fallback -- avoids the bug where an owner travelling overseas sees the wrong fiscal year"
    - "Manual @RequestParam validation via IllegalArgumentException + GlobalExceptionHandler.handleIllegalArgument -- avoids @Validated controller-level wiring complexity for a single param"

key-files:
  created:
    - backend/src/main/resources/tax/tax-parameters-tr.yml
    - backend/src/main/java/com/fintrack/report/tax/tr/TrTaxParameters.java
    - backend/src/main/java/com/fintrack/report/tax/tr/TrTaxParametersLoader.java
    - backend/src/main/java/com/fintrack/report/tax/tr/TrTaxReportResponse.java
    - backend/src/main/java/com/fintrack/report/tax/tr/TrTaxService.java
    - backend/src/main/java/com/fintrack/report/tax/tr/TrTaxController.java
    - backend/src/test/java/com/fintrack/report/tax/tr/TrTaxParametersLoaderTest.java
    - backend/src/test/java/com/fintrack/report/tax/tr/TrTaxServiceTest.java
    - backend/src/test/java/com/fintrack/report/tax/tr/TrTaxControllerWebMvcTest.java
    - backend/src/test/resources/tax-malformed/tax-parameters-tr.yml
    - frontend/src/types/tax.types.ts
    - frontend/src/api/taxtr.api.ts
    - frontend/src/hooks/useTrTaxReport.ts
    - frontend/src/hooks/useTrTaxReport.test.tsx
    - frontend/src/pages/TrTaxPage.tsx
  modified:
    - backend/src/main/java/com/fintrack/portfolio/dividend/DividendRepository.java
    - backend/src/test/java/com/fintrack/portfolio/dividend/DividendRepositoryDataJpaTest.java
    - frontend/src/App.tsx
    - frontend/src/pages/AnalyticsPage.tsx
    - frontend/src/i18n/locales/tr.json
    - frontend/src/i18n/locales/en.json
    - docs/OPERATIONS.md
    - .planning/STATE.md
  deliberately-untouched:
    - .env.example -- project deny rule Write/Edit(**/.env.*); no new env vars required (YAML is on the classpath, no external service)
    - docker-compose.yml -- project pre_guard_release_files.py PreToolUse hook; this plan introduces zero infra changes
    - CHANGELOG.md -- pre_guard_release_files.py covers it; per the 26-01/26-02/26-03 precedent, the changelog entry is described in this SUMMARY and applied by the release flow
    - frontend/openapi.json + frontend/src/api/openapi.types.ts -- the regen script (scripts/regen-openapi.sh) fails on a pre-existing OpenTelemetry sdk-autoconfigure ComponentLoader classpath issue from Phase 26-01 that affects pre-27-01 commits as well (verified by re-running the script at HEAD bd6cf2a). Fixing it would expand scope and add a Maven dep. The endpoint surface is exercised end-to-end by TrTaxControllerWebMvcTest.

key-decisions:
  - "YAML keyed by year over Java constants / Flyway migration / admin-settings table. Owner edits one file once a year (a 30-second job) and rebuilds. Java constants force a code change + deploy each January and inflate diffs with hard-coded numbers a non-developer cannot audit. Flyway turns the schema into an append-only history of tax constants, which is the wrong shape (constants are configuration, not reportable data). Admin-settings table is over-engineered for a single-owner homelab. Closed-year blocks stay in the YAML forever as the audit trail for past filings."
  - "80%/100% proximity bands hard-coded as service constants (APPROACHING_RATIO = 0.80). The bands are a UX convention, not a tax rule, so they live in TrTaxService and not in the YAML -- an owner edit cannot accidentally widen or narrow the warning window. status enum is a stable token set: under | approaching | over | unknown."
  - "Year-defaulting honours UserSettings.timezone with Europe/Istanbul fallback. When ?year is omitted, the controller derives the current year via LocalDate.now(zone) where zone comes from settings.getTimezone(); blank/missing/invalid timezone falls back silently to Europe/Istanbul. Avoids the bug where an owner travelling overseas sees the wrong fiscal year."
  - "Stoppage scope = dividend stoppage only. The TR tax code distinguishes dividend withholding (stopaj), capital-gains withholding (broker pre-collection on equities held < 1 year), and FX stoppage. This plan covers ONLY dividend stoppage because that is the data the platform already captures (Dividend.withholding_tax has been on every record since V34). Equity capital-gains and FX stoppage are deferred -- the platform does not capture broker-side withholding today."
  - "Capital-gains threshold scope follows parameters.appliesTo. Each year block declares which Asset.AssetType enum names the threshold applies to (default [STOCK] for 2024 + 2025 -- the listed-equity exemption rule). TrTaxService re-fetches each CapitalGainsResponse.Event's asset and filters by enum-name membership when computing the headline figure. Crypto, gold, funds appear in the response but do not count against the threshold. The assetType isn't on the Event record itself, so an asset cache (HashMap<UUID, Asset>) keeps the join cost O(unique-assets-this-year) instead of O(events)."
  - "No new migration. Dividend.withholding_tax (V34) is the read source; tax-parameters-tr.yml is YAML-on-classpath; TrTaxService is read-only and computes on the fly. The plan touches Java + YAML + frontend + tests; zero Flyway files."
  - "Service-level reuse of CapitalGainsService. TrTaxService.compute(...) calls capitalGainsService.compute(userId, year) to get realized gain + dividend net (and the realized gain is then re-scoped by appliesTo via the events list). The threshold comparison + parameter lookup + status banding are the only new logic; the FIFO lot-tracking that CapitalGainsService already encodes is not duplicated."
  - "Single-locale resource file. The plan ships only tax-parameters-tr.yml. A future locale (US, DE, ...) adds tax-parameters-{locale}.yml + a sibling XxTaxService / XxTaxController. Generic TaxParameters interfaces are deferred -- premature abstraction would force a second locale to inherit choices made for TR."
  - "Manual @RequestParam validation via IllegalArgumentException, NOT @Validated + @Min(2000). Spring Boot 3.2 maps ConstraintViolationException to 500 by default unless wired into a @ControllerAdvice handler that the project does not have today. Using IllegalArgumentException routes through the existing GlobalExceptionHandler.handleIllegalArgument -> 400 with the consistent ErrorResponse envelope; controller stays simple and the test surface is identical."
  - "No frontend chart, no CSV/PDF export, no scheduled threshold-crossed email. A 'headroom over time' Recharts line chart needs per-month rolling realized gain (CapitalGainsService doesn't expose it). PDF/CSV export of the tax report is a separate plan (could fold into Phase 13 export track). A weekly 'you crossed 80%' email is a @Scheduled worker, not a report endpoint. All deferred."

duration: 38 min
completed: 2026-05-07
---

# Phase 27 Plan 01: TR Tax Helper

**FinTrack ships a yearly tax helper at `/reports/tax/tr` that aggregates dividend stoppage gross/withholding/net per fiscal year across every active portfolio, surfaces the realized capital-gains figure (asset-class-scoped) next to the configurable annual exempt threshold, and emits a status (`under` / `approaching` / `over` / `unknown`) with the residual TRY headroom. Tax-year parameters live in a versioned YAML resource keyed by year (`backend/src/main/resources/tax/tax-parameters-tr.yml`) seeded with 2024 (158,000 TRY) and 2025 (200,000 TRY) thresholds sourced from GIB + Resmi Gazete announcements; the owner appends one block in January each year and rebuilds. The new `com.fintrack.report.tax.tr` package shape (`TrTaxParameters`, `TrTaxParametersLoader`, `TrTaxReportResponse`, `TrTaxService`, `TrTaxController`) reuses `CapitalGainsService.compute(...)` as a black box for the FIFO walker, layering only the threshold comparison + status banding + warnings list on top. A new lazy-loaded `TrTaxPage` renders four stat cards (status / realized YTD / threshold / headroom) plus a per-asset stoppage table and a parameters-source footer, with a tile linked from the Analytics page and a full `taxTr.*` i18n namespace in both `tr.json` and `en.json`.**

> **Operator Action — none required this plan.**
>
> No new env vars, no new docker services, no new Maven or npm deps, no new Flyway migration. The only owner-side touchpoint is the yearly YAML edit documented under `docs/OPERATIONS.md` -> "Updating TR tax parameters (yearly)".

## Performance

- Duration: 38 min
- Tasks executed: 5 / 5 (atomic commit per task per GSD protocol)
- Files created: 15 (`TrTaxParameters.java`, `TrTaxParametersLoader.java`, `TrTaxReportResponse.java`, `TrTaxService.java`, `TrTaxController.java`, `tax-parameters-tr.yml`, `TrTaxParametersLoaderTest.java`, `TrTaxServiceTest.java`, `TrTaxControllerWebMvcTest.java`, the malformed-yaml test fixture, `tax.types.ts`, `taxtr.api.ts`, `useTrTaxReport.ts`, `useTrTaxReport.test.tsx`, `TrTaxPage.tsx`)
- Files modified: 8 (`DividendRepository.java`, `DividendRepositoryDataJpaTest.java`, `App.tsx`, `AnalyticsPage.tsx`, `tr.json`, `en.json`, `docs/OPERATIONS.md`, `.planning/STATE.md`)
- Files deliberately untouched: 5 (`.env.example`, `docker-compose.yml`, `CHANGELOG.md`, `frontend/openapi.json`, `frontend/src/api/openapi.types.ts`)
- Test count delta backend: +21 (1090 -> 1111). Loader 4 + Service 11 + Controller 4 + Repository 2 (Docker-gated additive).
- Test count delta frontend: +2 (231 -> 233). useTrTaxReport hook test.
- Verify status: `./mvnw -B -ntp verify` green; JaCoCo 60% / 45% met; Spotless clean. Frontend `npm run lint && npm run typecheck && npm run build && npm run test --run` all green.

## Accomplishments

1. **Tax-parameter YAML resource + loader + record types.** `backend/src/main/resources/tax/tax-parameters-tr.yml` ships with `2024` (158,000 TRY) and `2025` (200,000 TRY) seeded from `https://www.gib.gov.tr/` and `https://www.resmigazete.gov.tr/` (access date recorded in the file header). `TrTaxParameters` immutable record carries `year`, `capitalGainsThresholdTry`, `appliesTo`, `dividendStoppageRate`, `besDividendStoppageRate`, `notes`, and `source`. `TrTaxParametersLoader` `@Component` `@PostConstruct`-loads the YAML via SnakeYAML (transitively present from 26-02 -- no new Maven dep), exposes `Optional<TrTaxParameters> findByYear(int)`, and silently degrades to an empty map on missing/malformed input so a typo never crashes boot. Loader test pins all four failure-mode branches (4 tests).

2. **TrTaxService + DividendRepository.sumStoppage queries + TrTaxReportResponse.** `DividendRepository` gains two aggregate queries (totals 3-tuple + per-asset breakdown). `TrTaxService.compute(userId, yearFilter)` resolves the year (explicit / from `UserSettings.timezone` / Europe/Istanbul fallback), short-circuits to a zero-filled response when the user has no portfolios, aggregates dividend stoppage, scopes realized capital gain to `parameters.appliesTo` (re-fetching each event's asset because `CapitalGainsResponse.Event` does not carry `AssetType`), computes `usedRatio` / `headroomTry`, and emits a `under | approaching | over | unknown` status with a stable warnings token list. Service test covers all 4 status branches, parameters-missing fallback, multi-portfolio aggregation, no-portfolio short-circuit, year-defaulting from both timezone settings and the Istanbul default, and asset-type filtering (11 tests).

3. **TrTaxController + WebMvc test.** `GET /api/v1/reports/tax/tr` mirrors the `CapitalGainsController` shape (JWT principal, optional `?year` query). Year validation is manual via `IllegalArgumentException` (routes through the existing `GlobalExceptionHandler.handleIllegalArgument` -> 400) so the controller does not need `@Validated` wiring or a new `ConstraintViolationException` handler. WebMvc test pins happy path JSON shape, year-omitted null delegation, year-below-minimum (`-5`) 400, and the principal-missing 5xx path that documents controller-level dependence on `SecurityConfig` (4 tests). The OpenAPI spec regen step from the plan was deferred (see Deviations).

4. **Frontend TrTaxPage + hook + API + i18n + Analytics tile.** Typed contract (`tax.types.ts` with strict null on `thresholdTry` / `headroomTry` / `usedRatio` when `parameters` is null), axios module (`taxtr.api.ts`), React Query hook (`useTrTaxReport.ts` with `staleTime: 60_000` mirroring `useCapitalGains`), and a lazy-loaded `TrTaxPage` rendering: four stat cards (Status with `ShieldCheck` / `ShieldAlert` / `ShieldX` / `ShieldQuestion` icons + tonal palette + status hint, Realized YTD, Threshold, Headroom), warnings banner that splits the `approaching-threshold:0.83` token shape, per-asset stoppage table with totals row, and a parameters-source footer that surfaces `parametersMissing` copy when `parameters` is null. Year picker pulls a hard-coded most-recent-five-years list because the report does not expose a `byYear` summary. Analytics page gains a tile linking to the route. `taxTr.*` i18n namespace ships in both `tr.json` and `en.json` with status / statusHint / warnings / stoppage / parametersMissing / source / notes / empty copy. Hook test pins year forwarding + `queryKey` shape (2 tests).

5. **OPERATIONS.md note + STATE.md update + cross-cutting sweep.** `docs/OPERATIONS.md` gains a new `## Updating TR tax parameters (yearly)` H2 section: 5-step workflow for the January YAML edit, explicit "verify both GIB + Resmi Gazete and record the access date in the file header" instruction, "do NOT edit a closed-year block" rule, and a `### Adding a new locale (e.g. US, DE)` subsection that points at the `tax-parameters-{locale}.yml` + sibling `XxTaxService` extension pattern with the explicit "out of scope for FinTrack v1.x -- single-tenant TR-domiciled" note. `.planning/STATE.md` reflects Phase 27 in progress (1/4 plans). `CHANGELOG.md`, `.env.example`, and `docker-compose.yml` deliberately left untouched per project guards.

## Files Created/Modified

**Created (backend):**
- `backend/src/main/resources/tax/tax-parameters-tr.yml` — seeded with 2024 + 2025; header records source URLs (GIB + Resmi Gazete) and access date.
- `backend/src/main/java/com/fintrack/report/tax/tr/TrTaxParameters.java` — immutable record carrying year + threshold + appliesTo + rates + notes + source.
- `backend/src/main/java/com/fintrack/report/tax/tr/TrTaxParametersLoader.java` — `@Component` `@PostConstruct` SnakeYAML loader with empty-map fallback on missing/malformed input.
- `backend/src/main/java/com/fintrack/report/tax/tr/TrTaxReportResponse.java` — DTO record with nested `DividendStoppage`, `AssetStoppage`, `CapitalGainsThreshold`.
- `backend/src/main/java/com/fintrack/report/tax/tr/TrTaxService.java` — threshold banding, realized-gain scoping, year-defaulting via timezone, no-portfolio short-circuit.
- `backend/src/main/java/com/fintrack/report/tax/tr/TrTaxController.java` — `GET /api/v1/reports/tax/tr`, manual year >= 2000 validation.
- `backend/src/test/java/com/fintrack/report/tax/tr/TrTaxParametersLoaderTest.java` — 4 cases.
- `backend/src/test/java/com/fintrack/report/tax/tr/TrTaxServiceTest.java` — 11 cases.
- `backend/src/test/java/com/fintrack/report/tax/tr/TrTaxControllerWebMvcTest.java` — 4 cases.
- `backend/src/test/resources/tax-malformed/tax-parameters-tr.yml` — fixture for the malformed-YAML loader branch.

**Created (frontend):**
- `frontend/src/types/tax.types.ts` — typed contract with strict null on threshold-block fields.
- `frontend/src/api/taxtr.api.ts` — axios module mirroring `capitalgains.api.ts` shape.
- `frontend/src/hooks/useTrTaxReport.ts` — React Query hook with `staleTime: 60_000`.
- `frontend/src/hooks/useTrTaxReport.test.tsx` — 2 cases pinning year forwarding + queryKey shape.
- `frontend/src/pages/TrTaxPage.tsx` — lazy-loaded page with status / realized / threshold / headroom cards + per-asset stoppage table + warnings banner + parameters-source footer.

**Modified:**
- `backend/src/main/java/com/fintrack/portfolio/dividend/DividendRepository.java` — `sumStoppageTotalsByPortfoliosAndRange` (3-tuple) + `sumStoppageByAssetAndPortfoliosAndRange` (per-asset breakdown).
- `backend/src/test/java/com/fintrack/portfolio/dividend/DividendRepositoryDataJpaTest.java` — 2 additive Docker-gated cases for the new aggregate queries.
- `frontend/src/App.tsx` — lazy import + `/reports/tax/tr` route inside `<ProtectedRoute>`.
- `frontend/src/pages/AnalyticsPage.tsx` — new tile linking to `/reports/tax/tr` with `Receipt` icon + i18n keys.
- `frontend/src/i18n/locales/tr.json` — `taxTr.*` namespace + `analytics.tiles.taxTr` block.
- `frontend/src/i18n/locales/en.json` — same key set in English.
- `docs/OPERATIONS.md` — new `## Updating TR tax parameters (yearly)` H2 + `### Adding a new locale` subsection.
- `.planning/STATE.md` — Phase 27 in progress (1/4 plans), 27-01 decision row, progress + resume pointer.

**Deliberately untouched:**
- `.env.example` — project deny rule `Write/Edit(**/.env.*)`. No new env vars; YAML is on the classpath.
- `docker-compose.yml` — `pre_guard_release_files.py` PreToolUse hook. Plan introduces zero infra changes.
- `CHANGELOG.md` — also covered by the release-files guard. Per the 26-01/26-02/26-03 precedent, the changelog entry is described in this SUMMARY and applied by the release flow.
- `frontend/openapi.json` and `frontend/src/api/openapi.types.ts` — see Deviations.

## Decisions Made

1. **Externalized YAML over Java constants / Flyway / admin settings** — owner edits one file once a year; no code change, no migration, no UI; closed-year blocks stay for audit trail.
2. **80%/100% proximity bands hard-coded as service constants** — UX convention, not a tax rule; lives in `TrTaxService` not the YAML so an owner edit cannot accidentally change them.
3. **Year-defaulting honours `UserSettings.timezone`** with `Europe/Istanbul` fallback — avoids the wrong-fiscal-year bug when the owner is overseas.
4. **Stoppage scope = dividend stoppage only** — equity capital-gains withholding and FX stoppage deferred (the platform does not capture broker-side withholding today).
5. **Capital-gains threshold scope follows `parameters.appliesTo`** — list of `Asset.AssetType` enum names, default `[STOCK]`; crypto/gold/funds appear in the response but do not count against the threshold.
6. **No new migration** — `Dividend.withholding_tax` (V34) is the read source; the YAML is on the classpath.
7. **Service-level reuse of `CapitalGainsService`** — `TrTaxService.compute(...)` consumes `capitalGainsService.compute(userId, year)` then layers the threshold comparison + status banding + asset-type scoping on top; no duplicated FIFO walker.
8. **Single-locale resource file** — generic `TaxParameters` interfaces deferred until a second locale lands.
9. **Manual `@RequestParam` validation via `IllegalArgumentException`** instead of `@Validated` + `@Min`, because the existing `GlobalExceptionHandler.handleIllegalArgument` already maps to 400 with the consistent error envelope (avoids adding a `ConstraintViolationException` handler).
10. **No frontend chart, no CSV/PDF export, no scheduled threshold-crossed email** — out of scope for the report endpoint; deferred to follow-up plans.

## Mutation Coverage Results

`pitest` is opt-in via the `mutation` Maven profile and is not part of this plan's verification. The project-level 60% / 45% JaCoCo gate runs on every `verify` and is green after this plan.

## Deviations from Plan

- **OpenAPI spec regen deferred.** `scripts/regen-openapi.sh` fails on a pre-existing `OpenTelemetry sdk-autoconfigure ComponentLoader` `NoClassDefFoundError` from Phase 26-01 that surfaces only when the full Spring context boots for the spec generator -- verified to also fail at HEAD `bd6cf2a` (the commit immediately before this plan), so the regression is NOT introduced by 27-01. Fixing it would require adding/upgrading an OpenTelemetry autoconfigure dep, which is a stop condition (no new Maven deps per the planner sanity check). The new endpoint is exercised end-to-end by `TrTaxControllerWebMvcTest`. The 23-03 contract test gate will catch the drift the moment the regen script is fixed; that fix should be its own follow-up plan.
- **Asset-type scoping required re-fetching assets per-event** because `CapitalGainsResponse.Event` does not carry `AssetType`; cached lookups in a `HashMap<UUID, Asset>` keep the join cost O(unique-assets-this-year).
- **WebMvc 401-without-auth case became a 5xx-with-null-principal case** because the `@AutoConfigureMockMvc(addFilters = false)` slice (used by every existing FinTrack `@WebMvcTest`) bypasses the JWT chain. The test still verifies that the controller cannot serve without a populated principal; in production `SecurityConfig` produces 401 first.
- **`TrTaxControllerWebMvcTest.reportFailsWhenPrincipalMissing` documents this filter-stripped behaviour** rather than testing `SecurityConfig` directly; full end-to-end auth is covered by integration tests elsewhere.
- **`@Validated` controller-level dropped** in favour of manual `IllegalArgumentException` validation -- see Decisions Made #9.
- **`SnakeYAML` was already on the classpath** via Sentry's transitive `org.yaml:snakeyaml:2.2` from 26-02 (verified via `mvn dependency:tree -Dincludes=org.yaml:snakeyaml`). No POM edit needed.
- **`DividendRepositoryDataJpaTest` extended additively** with two cases for the new aggregate queries; existing 6 cases unchanged. Docker-gated, so they run only when `dockerAvailable()` is true.

## Issues Encountered

- **Java 21 multi-catch for `ZoneRulesException | DateTimeException`** failed to compile because `ZoneRulesException extends DateTimeException`; replaced with a single `DateTimeException` catch covering both cases.
- **`stubRealizedAsSingleStockEvent(...)` was hard-coded to year 2025** in the service test, breaking the year-defaulting tests where the resolved year is "current calendar year". Refactored to a `stubRealizedAsSingleStockEventForYear(int year, ...)` helper with a backwards-compatible default. The 2-tier helper avoids `anyInt()` matchers on `capitalGainsService.compute(...)` that would weaken the multi-portfolio assertion.
- **OpenAPI spec generator boot path is broken** (see Deviations). Confirmed pre-existing by checking out `bd6cf2a` and re-running `scripts/regen-openapi.sh`.

## Next Phase Readiness

- **Phase 27 in progress (1 / 4 plans).** This plan stands up the tax helper end. The remaining sub-plans are independent of 27-01:
- **27-02 (accounts entity)** — does not consume code from 27-01.
- **27-03 (transactions wired to accounts)** — does not consume code from 27-01.
- **27-04 (TR bank CSV import)** — populates `Dividend` rows that this plan's stoppage aggregator picks up automatically; the wiring is `Dividend.withholding_tax` (V34), which 27-04 will write into and 27-01 reads from.
- **Future enhancement seeds** (deferred but seeded by this plan): per-month rolling realized-gain Recharts line chart, CSV/PDF export of the tax report, weekly "you crossed 80%" `@Scheduled` email alert, per-locale extension (`tax-parameters-us.yml` + `UsTaxService`), equity capital-gains and FX stoppage when broker-side withholding is captured.

## Next Step

Phase 27 in progress (1 / 4 plans). Next: `27-02` accounts entity. Run `/gsd:plan-phase 27 02`.
