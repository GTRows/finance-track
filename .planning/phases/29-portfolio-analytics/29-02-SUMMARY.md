---
phase: 29-portfolio-analytics
plan: 02
subsystem: analytics
tags: [correlation, heatmap, pearson, spearman, log-returns, intersection-alignment, caffeine, observed]

requires:
  - phase: 29
    plan: 01
    provides: AnalyticsPage Tabs bar + analytics package layout + Caffeine cache constant naming pattern (ANALYTICS_PORTFOLIOS_COMPARE_CACHE) -- the new tab and the new cache entry follow that mould byte-for-byte
  - phase: 1
    provides: PriceHistory entity + price_history table + 90-day retention -- the correlation matrix is read-only over this table
  - phase: 25
    plan: 02
    provides: Caffeine + Spring Cache wiring on CacheConfig -- the new ANALYTICS_CORRELATIONS_CACHE entry is appended to the same SimpleCacheManager bean
  - phase: 26
    plan: 01
    provides: Micrometer Observation @Observed annotation on classpath -- CorrelationService.compute carries @Observed(name = "analytics.correlations")

provides:
  - com.fintrack.analytics.correlation package: CorrelationController + CorrelationService + CorrelationMethod enum + CorrelationMatrixResponse + SamplePeriod DTOs
  - GET /api/v1/analytics/correlations endpoint returning a square symmetric correlation matrix over up to 25 assets
  - PriceHistoryRepository.findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc derived query
  - CacheConfig.ANALYTICS_CORRELATIONS_CACHE entry (60s TTL, 200 max) with @CacheEvict on PriceSyncService.persistUpdates / refreshAsset
  - frontend AssetCorrelationMatrix.tsx + useCorrelationMatrix + useHeldAssets hooks + analyticsApi.fetchCorrelationMatrix
  - third tab "Correlations" on the AnalyticsPage Tabs bar
  - analytics.correlations.* i18n namespace in both tr.json and en.json + analytics.tabs.correlations key

affects: [29-03]

tech-stack:
  added: []
  patterns:
    - "Pair-wise date intersection (NOT forward-fill) for sparse-data alignment. Forward-fill biases correlations toward 1.0 because flat days masquerade as zero-return days. The kernel iterates the upper triangle and intersects each pair's date keys before differencing."
    - "Multiple intra-day price-history rows collapse to the latest recordedAt per UTC calendar date matching BenchmarkService precedent. The kernel keys daily-close maps by `recordedAt.atZone(ZoneOffset.UTC).toLocalDate()`."
    - "Pearson kernel `corr(x, y) = sum((x - mx)(y - my)) / sqrt(sum((x - mx)^2) * sum((y - my)^2))` clipped to [-1, 1] to absorb floating-point wobble; Spearman is the same kernel applied to rank-encoded returns (1-based, ties via average rank)."
    - "Sorted-id Caffeine cache key so [A,B,C] and [C,B,A] share an entry. Mirrors the analytics:portfolios:compare key shape from 29-01 byte-for-byte."
    - "CSS-grid heatmap (no new charting dep) — `grid-template-columns: auto repeat(N, minmax(28px, 1fr))`; cells coloured via inline lerpColor(value); diagonal cells render '—', null cells render hatched + 'n/a'; one shared portal-rendered tooltip instead of N^2 per-cell tooltips."

key-files:
  created:
    - backend/src/main/java/com/fintrack/analytics/correlation/CorrelationController.java
    - backend/src/main/java/com/fintrack/analytics/correlation/CorrelationService.java
    - backend/src/main/java/com/fintrack/analytics/correlation/CorrelationMethod.java
    - backend/src/main/java/com/fintrack/analytics/correlation/dto/CorrelationMatrixResponse.java
    - backend/src/main/java/com/fintrack/analytics/correlation/dto/SamplePeriod.java
    - backend/src/test/java/com/fintrack/analytics/correlation/CorrelationServiceTest.java
    - backend/src/test/java/com/fintrack/analytics/correlation/CorrelationControllerWebMvcTest.java
    - frontend/src/components/analytics/AssetCorrelationMatrix.tsx
    - frontend/src/components/analytics/AssetCorrelationMatrix.test.tsx
  modified:
    - backend/src/main/java/com/fintrack/price/PriceHistoryRepository.java
    - backend/src/main/java/com/fintrack/price/PriceSyncService.java
    - backend/src/main/java/com/fintrack/common/config/CacheConfig.java
    - backend/src/test/java/com/fintrack/common/config/CacheConfigTest.java
    - backend/src/test/java/com/fintrack/price/PriceHistoryRepositoryDataJpaTest.java
    - frontend/src/api/analytics.api.ts
    - frontend/src/hooks/useAnalytics.ts
    - frontend/src/hooks/useAnalytics.test.tsx
    - frontend/src/pages/AnalyticsPage.tsx
    - frontend/src/i18n/locales/tr.json
    - frontend/src/i18n/locales/en.json
    - docs/API.md
    - .planning/STATE.md
    - .planning/ROADMAP.md
  deliberately-untouched:
    - .env.example -- project deny rule; no new env vars
    - docker-compose.yml -- locked by pre_guard_release_files.py; no infra changes
    - CHANGELOG.md -- locked; entry described in this SUMMARY and applied by the release flow
    - backend/pom.xml -- no new Maven dep
    - package.json + package-lock.json -- no new npm dep (CSS-grid heatmap deliberately chosen over a heatmap library)
    - frontend/openapi.json + frontend/src/api/openapi.types.ts -- regen still defers per the pre-existing 26-01 OpenTelemetry sdk-autoconfigure ComponentLoader NoClassDefFoundError; CorrelationControllerWebMvcTest covers the new endpoint contract

key-decisions:
  - "Sparse-data alignment is pair-wise date intersection — forward-fill would bias correlations toward 1.0 because flat days masquerade as zero-return days. The matrix kernel intersects each pair's date keys before differencing; `dataPoints[i][j]` carries the count of overlapping returns AFTER differencing (so 30 overlapping dates produce 29 returns)."
  - "Multiple intra-day price-history rows collapse to the latest recordedAt per UTC calendar date matching BenchmarkService precedent. price_history is written sub-hourly during market hours; the kernel collapses to one entry per LocalDate via `atZone(UTC).toLocalDate()`."
  - "No per-user holding-existence guard. HoldingRepository does NOT expose `existsByAssetIdInAndPortfolioUserId`, and assets are seeded globally (V2__seed_assets.sql), so the minimum guard is `assetRepository.findById(...)` (404 on unknown). The frontend asset picker filters to the operator's held-asset universe via useHeldAssets(); the server contract is 'any authenticated user can correlate any asset id'."
  - "25-asset cap enforced server-side (CORRELATION_TOO_MANY 400). Beyond 25, heatmap cells become unreadable on a 1280px page (cells under 24px) and SQL fan-out hurts cache-miss latency (25 round-trips already)."
  - "Default range is the last 90 days (matches price_history retention). Older `from` values clamp to today - 90d so the cache key stays stable. The frontend range picker maxes out at '90D' for the same reason."
  - "Diagonal cells emit 1.0 for valid rows and `null` for degenerate (stddev = 0 or n < 2 returns) rows; off-diagonal cells emit `null` under the same conditions. The frontend renders null cells with hatched background + 'n/a' text and the diagonal as `bg-muted` + em dash."
  - "method=PEARSON default with SPEARMAN as a secondary option. Spearman ranks returns first (1-based, ties via average rank) then runs the same Pearson kernel on the ranks. No third method (Kendall, distance correlation, etc.) in scope."
  - "@Cacheable on analytics:correlations (60s TTL, 200 max) keyed by `userId + sortedAssetIdsCsv + from + to + method`. PriceSyncService.persistUpdates and refreshAsset carry `@CacheEvict(allEntries = true)` so the cache invalidates whenever a new price-history row lands."
  - "@Observed boundary lives on CorrelationService.compute (NOT the controller) — mirrors the 26-01 / 29-01 / 28-02 precedent. The 26-01 servlet observation handler already covers the HTTP-level span."
  - "CSS-grid heatmap rather than a new charting library. Recharts has no heatmap primitive but a CSS grid of <button> cells with inline `style={{ backgroundColor: lerpColor(value) }}` is sufficient and accessible (`role='grid'`, per-cell `aria-label`). One shared portal-rendered tooltip instead of N^2 tooltips keeps the DOM small."

duration: 90 min
completed: 2026-05-09
---

# Phase 29 Plan 02: Asset Correlation Matrix

**FinTrack ships an asset correlation matrix at `/analytics` under a new `Correlations` tab. The operator picks 2..25 assets from the universe of currently-held assets across active portfolios, picks a range preset (`1M / 3M / 6M / YTD / 90D`) and a method (`Pearson` default, `Spearman` for outlier-robust rank correlation), and sees a colour-coded N x N heatmap of pairwise log-return correlations. Hover any cell for the precise value (3 decimal places), the pair's overlapping data-point count, and the method label. Cells are coloured on a diverging palette (red = anti-correlated, neutral grey = uncorrelated, green = correlated). Sparse data is handled by pair-wise date intersection (forward-fill is deliberately NOT used because it biases correlations toward 1.0). Diagonal cells render as em dashes; cells with insufficient data render as 'n/a' with a tooltip explaining the cause.**

> **Operator Action — none required this plan.**
>
> No new env vars, no new docker services, no new Maven or npm deps. No Flyway migration. The new endpoint is reachable on next backend boot at `GET /api/v1/analytics/correlations`. First-use lives at `/analytics` → `Correlations` tab.

## Performance

- Duration: 90 min (across 7 atomic commits per GSD protocol)
- Tasks executed: 7 / 7
- Files created: 9 (5 backend Java + 2 backend test classes + 1 frontend component + 1 frontend test class)
- Files modified: 14 (3 backend main + 2 backend test + 5 frontend + 2 i18n + docs/API.md + STATE.md + ROADMAP.md)
- Files deliberately untouched: 7 (`.env.example`, `docker-compose.yml`, `CHANGELOG.md`, `backend/pom.xml`, `package.json`, `package-lock.json`, `frontend/openapi.json` + `frontend/src/api/openapi.types.ts`)
- Test count delta backend: +22 (1364 → 1386). 15 CorrelationServiceTest unit cases + 7 CorrelationControllerWebMvcTest cases. Exceeds the +20 plan target. Two additive Docker-gated PriceHistoryRepositoryDataJpaTest cases plus one CacheConfigTest extension are also added but Docker-gated cases skip on the CI runner.
- Test count delta frontend: +7 (261 → 268). 5 AssetCorrelationMatrix.test.tsx cases + 2 useAnalytics.test.tsx cases. Meets the +7 plan target exactly.
- Verify status: `./mvnw -B -ntp verify` green; JaCoCo 60% / 45% met (`All coverage checks have been met.`); Spotless clean (590 files clean). Frontend `npm run lint -- --max-warnings 0`, `npx tsc --noEmit`, `npm run test -- --run`, `npm run build` all green.

## Accomplishments

1. **Task 1 — Inspection-only.** Confirmed canonical names: `PriceSyncService.persistUpdates(List<PriceUpdate>)` is the `@Transactional` write boundary that calls `priceHistoryRepository.save(...)` via the private `recordHistory(...)` helper; `refreshAsset(UUID)` is the alternate single-asset path. Both received `@CacheEvict`. `HoldingRepository` does NOT expose `existsByAssetIdInAndPortfolioUserId`, so the optional ownership guard was dropped (decision logged).

2. **Task 2 — Backend repository + DTO + enum + service + controller + cache + evict.** New `com.fintrack.analytics.correlation` package with five files: `CorrelationController` (mounts `GET /api/v1/analytics/correlations`), `CorrelationService` (orchestrates resolution + log-return alignment + Pearson / Spearman kernels), `CorrelationMethod` enum, `CorrelationMatrixResponse` + `SamplePeriod` DTO records. New derived-query method `findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc` on `PriceHistoryRepository`. `CacheConfig` registers `ANALYTICS_CORRELATIONS_CACHE` (60s TTL, 200 max). `PriceSyncService.persistUpdates` and `refreshAsset` carry `@CacheEvict(value = ANALYTICS_CORRELATIONS_CACHE, allEntries = true)`.

3. **Task 3 — Backend tests.** `CorrelationServiceTest` (15 cases — empty / single-asset guards, perfectly-correlated r=1, anti-correlated r=-1, low-magnitude uncorrelated, zero-stddev null surfacing, sparse pair-wise intersection counts, dedup, max-cap, range validation, unknown asset 404, default 90-day window, Spearman vs Pearson on monotonic-non-linear series, insufficient overlap n=0, intra-day collapse to latest). `CorrelationControllerWebMvcTest` (7 cases — happy 200, missing param 400, malformed UUID 400, BusinessRuleException 400 with envelope code, too-many-assets 400, default PEARSON, accepts SPEARMAN literal). 2 additive Docker-gated cases on `PriceHistoryRepositoryDataJpaTest` for the new derived query. `CacheConfigTest` extended additively with the new `ANALYTICS_CORRELATIONS_CACHE` entry.

4. **Task 4 — Frontend matrix component + hook + API.** New `AssetCorrelationMatrix.tsx` owns the asset multi-select Popover (mirrors `PortfolioMultiSelect` from 29-01 byte-for-byte; selection cap 25), range preset row (`1M / 3M / 6M / YTD / 90D`), method toggle (`Pearson / Spearman` mirroring 29-01 ModeToggle), CSS-grid heatmap, and shared portal-rendered tooltip. `useCorrelationMatrix(assetIds, from, to, method)` appended to `useAnalytics.ts` with sorted-id queryKey and `placeholderData: keepPreviousData`. Colocated `useHeldAssets()` composes `useQueries` over each portfolio's `holdingApi.list` to dedupe the held-asset universe. `analyticsApi.fetchCorrelationMatrix` + `CorrelationMatrixResponse` + `CorrelationMethodLiteral` types appended to `analytics.api.ts`. `AnalyticsPage` extends the `AnalyticsTab` union to include `'correlations'`, adds the third tab button, short-circuit-renders `<AssetCorrelationMatrix />` when active.

5. **Task 5 — Frontend tests + i18n.** `AssetCorrelationMatrix.test.tsx` (5 cases — empty state when < 2 assets selected, N x N gridcell render count, diagonal '—' cells, null `n/a` cells, method toggle Pearson → Spearman triggers a refetch with `method: 'SPEARMAN'`). `useAnalytics.test.tsx` extended with 2 cases (URL forwarding via `analyticsApi.fetchCorrelationMatrix` mock, method-flip Pearson → Spearman bumps the cache key into a separate entry). New `analytics.correlations.*` i18n namespace + `analytics.tabs.correlations` key in BOTH `tr.json` AND `en.json`.

6. **Task 6 — Docs + STATE.** `docs/API.md` extends with `### GET /api/v1/analytics/correlations` under the Analytics group: query parameter contract, validation error codes (`CORRELATION_IDS_REQUIRED` / `CORRELATION_TOO_FEW` / `CORRELATION_TOO_MANY` / `CORRELATION_RANGE_INVALID` / `INVALID_PARAMETER`), intersection-only sparse-data semantics, 90-day retention cap, Caffeine cache contract. `.planning/STATE.md` flips Phase 29 to `In progress (2/3 plans)` with the 29-02 decision row logged. OpenAPI regen attempted; defers per the pre-existing 26-01 OpenTelemetry classpath issue (documented under Deviations).

7. **Task 7 — SUMMARY + ROADMAP tick.** `.planning/ROADMAP.md` ticks the 29-02 line item under `Phase 29 Plans`. This SUMMARY file logs the seven decisions made + accomplishments + deviations + verification output.

## Files Created/Modified

**Created (backend):**
- `backend/src/main/java/com/fintrack/analytics/correlation/CorrelationController.java`
- `backend/src/main/java/com/fintrack/analytics/correlation/CorrelationService.java`
- `backend/src/main/java/com/fintrack/analytics/correlation/CorrelationMethod.java`
- `backend/src/main/java/com/fintrack/analytics/correlation/dto/CorrelationMatrixResponse.java`
- `backend/src/main/java/com/fintrack/analytics/correlation/dto/SamplePeriod.java`
- `backend/src/test/java/com/fintrack/analytics/correlation/CorrelationServiceTest.java`
- `backend/src/test/java/com/fintrack/analytics/correlation/CorrelationControllerWebMvcTest.java`

**Created (frontend):**
- `frontend/src/components/analytics/AssetCorrelationMatrix.tsx`
- `frontend/src/components/analytics/AssetCorrelationMatrix.test.tsx`

**Modified:**
- `backend/src/main/java/com/fintrack/price/PriceHistoryRepository.java` — new derived query.
- `backend/src/main/java/com/fintrack/price/PriceSyncService.java` — `@CacheEvict` on `persistUpdates` + `refreshAsset`.
- `backend/src/main/java/com/fintrack/common/config/CacheConfig.java` — `ANALYTICS_CORRELATIONS_CACHE` entry.
- `backend/src/test/java/com/fintrack/common/config/CacheConfigTest.java` — additive cache name + Caffeine-instance assertions.
- `backend/src/test/java/com/fintrack/price/PriceHistoryRepositoryDataJpaTest.java` — 2 additive Docker-gated cases.
- `frontend/src/api/analytics.api.ts` — `fetchCorrelationMatrix` method + types.
- `frontend/src/hooks/useAnalytics.ts` — `useCorrelationMatrix` + `useHeldAssets` hooks.
- `frontend/src/hooks/useAnalytics.test.tsx` — 2 additive cases.
- `frontend/src/pages/AnalyticsPage.tsx` — `'correlations'` tab + third button + short-circuit render.
- `frontend/src/i18n/locales/tr.json` — `analytics.correlations.*` namespace + `analytics.tabs.correlations`.
- `frontend/src/i18n/locales/en.json` — same key set in English.
- `docs/API.md` — new `### GET /api/v1/analytics/correlations` H3 under the Analytics group.
- `.planning/STATE.md` — Phase 29 status flips to 2/3 plans, 29-02 decision row, resume pointer.
- `.planning/ROADMAP.md` — 29-02 line item ticked.

**Deliberately untouched:**
- `.env.example` — project deny rule; no new env vars.
- `docker-compose.yml` — locked; no infra changes.
- `CHANGELOG.md` — locked; entry described here and applied by release flow.
- `backend/pom.xml` — no new Maven dep.
- `package.json` + `package-lock.json` — no new npm dep (CSS-grid heatmap chosen over any heatmap library).
- `frontend/openapi.json` + `frontend/src/api/openapi.types.ts` — see Deviations.

## Decisions Made

1. **Sparse-data alignment is pair-wise date intersection — NOT forward-fill.** Forward-fill biases correlations toward 1.0 because flat days look like zero-return days. The kernel intersects each pair's date keys before differencing.
2. **Multiple intra-day price-history rows collapse to the latest `recordedAt` per UTC calendar date** matching `BenchmarkService` precedent.
3. **No per-user holding-existence guard.** `HoldingRepository` does NOT expose `existsByAssetIdInAndPortfolioUserId`, and assets are seeded globally (V2). The minimum guard is `assetRepository.findById(...)` (404 on unknown). The frontend picker filters to the operator's held-asset universe via `useHeldAssets()`.
4. **25-asset cap enforced server-side** (`CORRELATION_TOO_MANY` 400). Beyond 25, heatmap cells become unreadable on a 1280px page and SQL fan-out hurts cache-miss latency.
5. **Default range is the last 90 days (matches `price_history` retention).** Older `from` values clamp to `today - 90d` so the cache key stays stable. Frontend range picker maxes out at `90D`.
6. **Diagonal cells emit 1.0 for valid rows and `null` for degenerate (stddev = 0 or n < 2) rows; off-diagonal cells emit `null` under the same conditions.** Frontend renders null cells with hatched background + 'n/a' text and the diagonal as `bg-muted` + em dash.
7. **`method=PEARSON` default with `SPEARMAN` as a secondary option.** Spearman ranks returns first then runs the same Pearson kernel on the ranks. No third method in scope.
8. **`@Cacheable` on `analytics:correlations` (60s TTL, 200 max) keyed by `userId + sortedAssetIdsCsv + from + to + method`.** `PriceSyncService.persistUpdates` and `refreshAsset` carry `@CacheEvict(allEntries = true)`.
9. **`@Observed` boundary lives on `CorrelationService.compute` (NOT the controller)** — mirrors 26-01 / 29-01 / 28-02 precedent.
10. **CSS-grid heatmap rather than a new charting dep.** A grid of `<button>` cells with `style={{ backgroundColor: lerpColor(value) }}` is sufficient and accessible. One shared portal-rendered tooltip keeps the DOM small.

## Deferred Enhancements

- **Saved correlation presets** — saving "BTC + BIST + GOLD over 1M" as a named view is out of scope; would need a new `correlation_presets` table.
- **CSV export of the matrix** — operator already has `/reports/capital-gains` for spreadsheet output; correlation matrix is a screen-first analysis.
- **Live WebSocket push of the matrix** — would require server-side incremental recomputation on each price tick; materially more complex than the periodic-recompute model.
- **Third correlation method (Kendall's tau, distance correlation)** — Pearson + Spearman covers the standard ask; Kendall is rarely the question for return-series.
- **Per-asset cache eviction** — `@CacheEvict(allEntries = true)` is the simplest correct policy; per-asset eviction would require keying entries by an asset-id bitmap. The cache caps at 200 × ~5 KB ≈ 1 MB so full eviction on each price-sync tick is acceptable.

## Test Counts

- Backend: 1364 → 1386 (+22). Plan target: +20 minimum. Met with margin.
- Frontend: 261 → 268 (+7). Plan target: +7. Met exactly.

## Verification Output

```
$ ./mvnw -B -ntp verify
[INFO] Tests run: 1386, Failures: 0, Errors: 0, Skipped: 151
[INFO] --- jacoco:0.8.12:check (check) @ fintrack-backend ---
[INFO] All coverage checks have been met.
[INFO] Spotless.Java is keeping 590 files clean - 0 needs changes to be clean
[INFO] BUILD SUCCESS

$ npm run lint -- --max-warnings 0
> eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0
(clean)

$ npx tsc --noEmit
(clean)

$ npm run test -- --run
Test Files  66 passed (66)
Tests  268 passed (268)

$ npm run build
✓ built in 5.45s
```

## Deviations from Plan

1. **OpenAPI spec regen continues to defer per the pre-existing 26-01 OpenTelemetry sdk-autoconfigure `ComponentLoader` `NoClassDefFoundError`** (verified at HEAD — `bash scripts/regen-openapi.sh` fails the same way it has since 27-01). Fixing the regen script needs its own follow-up plan because the root cause is in the OTel starter classpath. The new endpoint surface is exercised end-to-end by `CorrelationControllerWebMvcTest`. The 23-03 contract gate will catch endpoint drift the moment the regen script is fixed.

2. **The plan's "perfectly correlated geometric series → r = 1.0 ± 1e-9" test case** evaluates `Math.log(110.0/100.0)` etc. In double precision, `121.0 / 110.0` is NOT bit-identical to `110.0 / 100.0`, so the constant-return assumption breaks: the kernel observes a tiny non-zero stddev rather than exactly zero, and self-correlation comes back as `1.0` rather than `null`. The test was rewritten as `variedButPerfectlyCorrelatedSeriesReturnsOne` (B = 2 × A on every day → identical log returns → r = 1.0 exactly) plus a separate `uncorrelatedSeriesYieldsLowMagnitude` case. The mathematical claim — Pearson on linearly-related returns yields r = 1 — is preserved; only the test fixture changed.

3. **The plan called for `./mvnw -Pcoverage verify`** but the `coverage` Maven profile does not exist on this repo (Maven warns `The requested profile "coverage" could not be activated because it does not exist.`). JaCoCo coverage check runs unconditionally as part of the default `verify` lifecycle (`[INFO] All coverage checks have been met.`), so the gate is met without the profile activation. No code change needed.

## Rollback

`git revert` of this plan's commits is sufficient — no Flyway migration was added so nothing in the schema needs reverting. Cache entries naturally TTL out within 60 seconds; in-flight requests finish on the prior code paths because the package is purely additive.
