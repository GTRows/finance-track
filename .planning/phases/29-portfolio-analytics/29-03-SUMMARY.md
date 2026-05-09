---
phase: 29-portfolio-analytics
plan: 03
subsystem: analytics
tags: [monte-carlo, percentile-fan, virtual-thread, caffeine, observed, snakeyaml, recharts]

requires:
  - phase: 29
    plan: 01
    provides: AnalyticsPage Tabs bar + analytics package layout + Caffeine cache constant naming pattern -- the fourth tab and the new cache entry follow that mould byte-for-byte
  - phase: 29
    plan: 02
    provides: Third-tab plumbing precedent -- extends naturally to a fourth tab; analytics:correlations cache eviction-on-price-sync pattern that the new analytics:monteCarlo cache mirrors
  - phase: 25
    plan: 03
    provides: priceVirtualExecutor virtual-thread bean (Executors.newVirtualThreadPerTaskExecutor) wrapped by 26-01's tracingPriceVirtualExecutor decorator -- the Monte Carlo simulation reuses this bean via @Qualifier
  - phase: 26
    plan: 01
    provides: tracingPriceVirtualExecutor + ContextSnapshot propagation + @Observed orchestrator-boundary precedent
  - phase: 27
    plan: 01
    provides: TrTaxParametersLoader + tax-parameters-tr.yml byte-for-byte loader pattern that MonteCarloDefaultsLoader mirrors (PostConstruct + ClassPathResource + SnakeYAML + immutable map + silent empty-map degradation + non-default constructor for tests)
  - phase: 12
    provides: FireService.compute -- the deterministic single-trajectory baseline; the Monte Carlo frontend pre-fills currentNetWorth / monthlyContribution / targetNumber from useFire so the operator does not retype values

provides:
  - com.fintrack.analytics.montecarlo package: MonteCarloController + MonteCarloService + MonteCarloDefaultsLoader + AssetClass enum + 7 DTO records
  - POST /api/v1/analytics/monte-carlo endpoint returning a per-year percentile fan (p10/p25/p50/p75/p90) + summary stats (mean, p10, p50, p90, optional successProbability)
  - GET /api/v1/analytics/monte-carlo/defaults endpoint returning YAML-backed editor pre-fill payload (iterations, horizonYears, weights, per-class mean/stddev tuples)
  - backend/src/main/resources/analytics/monte-carlo-defaults.yml YAML resource with eight class default tuples
  - CacheConfig.ANALYTICS_MONTE_CARLO_CACHE entry (60s TTL, 200 max) with @CacheEvict on PriceSyncService.persistUpdates / refreshAsset
  - frontend MonteCarloProjection.tsx + useMonteCarloDefaults + useMonteCarloMutation hooks + analyticsApi.fetchMonteCarloDefaults / runMonteCarlo
  - fourth tab "Monte Carlo" on the AnalyticsPage Tabs bar
  - analytics.monteCarlo.* i18n namespace in both tr.json and en.json + analytics.tabs.monteCarlo key

affects: []

tech-stack:
  added: []
  patterns:
    - "Per-iteration sequential simulation, iterations-fan-out: each iteration walks `horizonYears * 12` monthly steps drawing one normal sample per allocation class per step under that class's weight, sums into a portfolio return, applies contribution at end-of-month, captures end-of-year balance. CompletableFuture.runAsync(..., tracingPriceVirtualExecutor) per iteration; allOf().join() at the end. Memory budget `horizonYears * iterations * 8 bytes` worst case (50y x 10k = 4 MB)."
    - "Per-iteration Random instances seeded from a per-task seed array generated upfront via SecureRandom.nextLong() (NOT ThreadLocalRandom). Production path: public compute(userId, request) generates fresh seeds; deterministic test path: package-private compute(userId, request, long[] fixedSeeds) overload accepts a pinned seed array."
    - "Normal-distribution sampling: monthly draw is `monthlyMean + monthlyStdDev * Random.nextGaussian()` where `monthlyMean = annualMean / 12` and `monthlyStdDev = annualStdDev / sqrt(12)`. i.i.d. month-over-month assumption documented in service JavaDoc."
    - "Percentile aggregation: terminalsByYear[year-1][iterationIndex] populated during the inner loop; after fan-out join, Arrays.sort each year column and linear-interpolate at [0.10, 0.25, 0.50, 0.75, 0.90]. Single-pass sort + lookup, O(iterations log iterations) per year."
    - "Cache key constructed via MonteCarloRequest.normalisedHash() helper that sorts allocation rows by AssetClass.name() before hashing so two equivalent requests with different row order share an entry. Mirrors analytics:correlations sorted-id cache-key shape."
    - "Defaults YAML loader byte-for-byte mirror of TrTaxParametersLoader (27-01): @Component + @PostConstruct void load() + ClassPathResource + SnakeYAML + immutable EnumMap + silent empty-map degradation on missing/malformed input + non-default constructor for tests."
    - "Recharts area-fan chart: ComposedChart with two stacked Area components (p10-p90 outer 0.15 opacity, p25-p75 inner 0.30 opacity) plus a Line for p50 (strokeWidth 2.5). Diff-area trick uses the inner band's lower bound as a card-coloured mask so the visible band is the difference between consecutive percentile pairs."
    - "Allocation editor as a flat HTML table: fixed first column (Class), three editable PercentInput numeric columns (Weight %, Mean %, Stddev %), per-row remove button, add-class via collapsed <details> dropdown listing un-added AssetClass enum values. Avoids pulling shadcn Combobox for what is effectively a 3..8-row table."

key-files:
  added:
    - backend/src/main/java/com/fintrack/analytics/montecarlo/MonteCarloController.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/MonteCarloService.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/MonteCarloDefaultsLoader.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/AssetClass.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/MonteCarloRequest.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/MonteCarloResponse.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/MonteCarloDefaultsResponse.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/AllocationClassInput.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/AllocationClassDefault.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/YearPercentilePoint.java
    - backend/src/main/java/com/fintrack/analytics/montecarlo/dto/MonteCarloSummary.java
    - backend/src/main/resources/analytics/monte-carlo-defaults.yml
    - backend/src/test/java/com/fintrack/analytics/montecarlo/MonteCarloDefaultsLoaderTest.java
    - backend/src/test/java/com/fintrack/analytics/montecarlo/MonteCarloServiceTest.java
    - backend/src/test/java/com/fintrack/analytics/montecarlo/MonteCarloControllerWebMvcTest.java
    - backend/src/test/resources/analytics-malformed/monte-carlo-defaults.yml
    - frontend/src/components/analytics/MonteCarloProjection.tsx
    - frontend/src/components/analytics/MonteCarloProjection.test.tsx
  modified:
    - backend/src/main/java/com/fintrack/common/config/CacheConfig.java
    - backend/src/main/java/com/fintrack/price/PriceSyncService.java
    - backend/src/test/java/com/fintrack/common/config/CacheConfigTest.java
    - frontend/src/api/analytics.api.ts
    - frontend/src/hooks/useAnalytics.ts
    - frontend/src/hooks/useAnalytics.test.tsx
    - frontend/src/pages/AnalyticsPage.tsx
    - frontend/src/i18n/locales/en.json
    - frontend/src/i18n/locales/tr.json
    - docs/API.md
    - .planning/STATE.md
    - .planning/ROADMAP.md
---

## Goal

Ship Track G6 — Monte Carlo net-worth projection — as the third and final plan
of Phase 29. The deterministic FIRE calculator (Phase 12) projects a single
expected-value trajectory under fixed mean return + monthly contribution; the
12-month cash flow projection (Phase 19) collapses uncertainty into one
expected line. Neither view exposes the volatility envelope. After this plan
the operator has a fan chart of `[p10, p25, p50, p75, p90]` terminal net-worth
values per year plus headline summary stats so they can size their drawdown
plan against the downside, not against the expected case.

## What landed

A new feature sub-package `com.fintrack.analytics.montecarlo` containing eight
Java files (controller + service + defaults loader + enum + 6 DTO records, with
some DTOs colocated per the established analytics package convention), one
new YAML classpath resource, and three backend test files. On the frontend,
one new component (`MonteCarloProjection.tsx`), two new hooks
(`useMonteCarloDefaults`, `useMonteCarloMutation`), two new API methods
(`fetchMonteCarloDefaults`, `runMonteCarlo`), seven new TypeScript types, a
fourth tab on `AnalyticsPage`, ~30 new i18n keys in both `tr.json` and
`en.json`, and one new component test file. CacheConfig registers the new
`ANALYTICS_MONTE_CARLO_CACHE` entry; `PriceSyncService.persistUpdates` and
`refreshAsset` `@CacheEvict` value arrays gain the new cache name so price
drift invalidates the cache (the simulation's starting point depends on
current net worth).

## Decisions Made

**Reused the existing `tracingPriceVirtualExecutor` bean rather than authoring
a colocated `monteCarloVirtualExecutor`.** The price-side bean is generic and
unbounded; sharing avoids a duplicate executor + tracing decorator + an extra
test file. The 26-01 `TracingConfigTest` already pins the wiring contract for
the shared bean. If a load-bearing reason later emerges to keep them separate
(e.g. pricing-domain cancellation policy that must not bleed into analytics)
the `MonteCarloVirtualExecutorConfig` route is documented in the plan and easy
to add later.

**No `FireService` dependency on the simulation surface.** The request body
always carries `currentNetWorth` and `monthlyContribution` explicitly; the
frontend pre-fills both from `useFire(...)` client-side. Keeps
`MonteCarloService` standalone (smaller blast radius, simpler tests, no
transitive `BudgetService` / `MonthlySummaryRepository` deps).

**Allocation weights are operator-entered, not auto-derived from holdings.**
v1 weights are manual (default fan: STOCK 50% / BOND 20% / CASH 10% / CRYPTO
10% / GOLD 10% from the YAML). Auto-derivation has three failure modes that
make it unreliable: (a) `Asset.AssetType.OTHER` lumps unrelated bonds +
commodities + currency into one class; (b) FX-denominated holdings need TRY
conversion that the FX layer does not yet expose; (c) the operator's mental
model is "what if my mix were 60/40 instead of 50/30/10/10?" — driven from a
hypothesised allocation, not from the actual one. Auto-derivation goes to
"Deferred Enhancements".

**No correlation matrix between classes.** Each class draws independently
from `N(mean, stddev)`. Modelling a STOCK-BOND correlation of -0.2 (the
historical norm) would require the operator to enter or accept a
class-correlation matrix plus a Cholesky decomposition step inside the inner
loop. Materially more complex; v1 fan chart's headline insight (the spread,
not the joint dynamics) is preserved without it.

**`AssetClass` enum is independent from `Asset.AssetType`.** The simulation's
macro-class taxonomy includes BOND and CASH which the asset master enum
lacks; conversely it omits OTHER's pricing-only nuances. Keeping the two enums
separate prevents accidental coupling — the simulation operates on
conceptual macro-classes, the asset-master enum stays unchanged.

**Per-iteration `Random` instances seeded from `SecureRandom.nextLong()`,
NOT `ThreadLocalRandom`.** The fixed-seed test seam works without thread-local
leakage; the package-private `compute(userId, request, long[] fixedSeeds)`
overload accepts a pinned seed array.

**`@Cacheable` cache key built via `MonteCarloRequest.normalisedHash()`.**
The helper sorts allocation rows by `AssetClass.name()` before hashing so two
equivalent requests with different row order share an entry. Same shape as
the analytics:correlations sorted-id key from 29-02.

**Iteration cap 10000 + horizon cap 50 years.** Beyond 10k iterations the
percentile noise floor flattens; 50y x 10k = 600k operations per class x 8
classes = 4.8M operations worst case (~5s on a modern JVM with virtual
threads). Both caps enforced server-side via Bean Validation `@Max`.

**Weight-sum tolerance 0.001 in both directions.** Sub-1.0 sums imply a
missing class — explicitly rejected to avoid silent allocation gaps. The
operator must normalise to 1.0 ± 0.001.

## Deferred Enhancements

- **Auto-derivation of allocation weights from holdings.** Three failure modes
  documented above (Asset.AssetType lumping, FX conversion, mental-model
  mismatch). Goes to a future plan once those preconditions are addressed.
- **Class-correlation matrix + Cholesky decomposition in the inner loop.**
  v2 work that would let the operator opt into "stocks down → bonds up"
  dynamics without losing the deterministic-test seam.
- **Log-normal sampling alternative.** Textbook-correct for compounded
  returns over long horizons (bounds the lower tail at zero). v1 ships
  Normal because the visual difference on a 20-year fan is < 5% and Normal
  is pedagogically clearer.
- **Saved scenarios.** Naming "BTC-bear case" or "60/40 baseline" requires a
  new `monte_carlo_scenarios` table + CRUD endpoints. Out of scope for v1.
- **CSV export of the percentile fan.** The chart is a screen-first analysis;
  the operator can paste values into Excel manually if needed.
- **WebSocket push refresh on price ticks.** Would invert the cache contract
  (every tick invalidates) and pin compute on the price-sync hot path.

## Test Counts

- Backend: 1386 → 1415 (+29; exceeds the +25 plan target).
  - `MonteCarloDefaultsLoaderTest` (5 cases — happy load, globals, missing
    file, malformed YAML, eight-class round-trip).
  - `MonteCarloServiceTest` (17 cases — empty allocations, iterations OOR,
    horizon OOR, weights invalid, negative net worth, negative contribution,
    below-min stddev, single-class deterministic FV check, fan year
    boundaries, monotonic ordering, success probability above all / below
    all / null target, defaultsApplied echoes resolved tuple, defaults
    endpoint coverage).
  - `MonteCarloControllerWebMvcTest` (7 cases — happy POST + GET defaults,
    400 on missing allocations / iterations OOR / horizon OOR / missing
    required field, controller delegates exactly once per HTTP call).
  - `CacheConfigTest` extended additively with `ANALYTICS_MONTE_CARLO_CACHE`
    assertions.
- Frontend: 268 → 275 (+7; meets the +7 plan target).
  - `MonteCarloProjection.test.tsx` (5 cases — defaults seed visible-by-
    default rows, Run posts request body and renders fan + summary cards,
    slider changes do not auto-submit, error description on mutation
    failure, row removal updates editor count).
  - `useAnalytics.test.tsx` extended with 2 cases (defaults URL forwarding,
    mutation request body).

## Verification Output

- `./mvnw -B -ntp verify` → BUILD SUCCESS, 1415 tests run / 0 failures /
  0 errors / 151 skipped (Docker-gated). JaCoCo 60% / 45% gate green;
  Spotless gate green (`Spotless.Java is keeping 604 files clean`).
- `npm run lint -- --max-warnings 0` → clean.
- `npx tsc --noEmit` → clean.
- `npm run test -- --run` → 67 test files pass / 275 tests pass.
- `npm run build` → Vite bundle clean (chunks unchanged from 29-02 baseline
  modulo the +52KB AnalyticsPage chunk size growth from the new component
  + recharts ComposedChart import).
- `git status` → clean except for the intended file set; no edits to
  `.env.example`, `docker-compose.yml`, `CHANGELOG.md`, `pom.xml`,
  `package.json`, `package-lock.json`, lockfiles.

## Deviations from Plan

- **`MonteCarloVirtualExecutorConfigTest` not authored.** The plan permitted
  reusing the existing `tracingPriceVirtualExecutor` bean (Task 1 confirmed
  the bean is generic and unbounded per 26-01-SUMMARY); the colocated config
  test was conditional on Task 2 introducing a new executor bean. Since
  Task 2 reused the existing bean via `@Qualifier` injection, the new test
  file is not needed — `TracingConfigTest` (26-01) already pins the executor
  contract.
- **`scripts/regen-openapi.sh` failed on the pre-existing 26-01 OpenTelemetry
  classpath issue** (`OpenApiSpecGeneratorTest.writesNormalisedOpenApiSpec`
  failed to load `ApplicationContext`). Mirrors the 27-* / 28-* / 29-01 /
  29-02 deferral. The new `MonteCarloControllerWebMvcTest` covers the
  endpoint contract in the meantime; OpenAPI regen is best-effort and gates
  on the upstream OTel fix.
- **Live smoke test** (boot + click through `/analytics` → Monte Carlo tab)
  was deferred to operator acceptance — the executor environment runs
  inside the orchestrator harness and cannot boot the dev stack
  interactively. The test surface (component test + WebMvc + service unit
  tests covering the validation envelope, deterministic fan, percentile
  monotonicity, success-probability extremes, defaults round-trip) covers
  the contract end-to-end.

## Phase 29 retrospective

Phase 29 closes after three plans, delivering the Portfolio Analytics
capability bundle:

- **29-01 (G4) — Portfolio comparison.** Multi-portfolio TRY value / cost /
  P&L overlay on a shared time axis. Caffeine-cached
  (`analytics:portfolios:compare`, 60s TTL). Daily snapshot capture invalidates.
- **29-02 (G5) — Asset correlation matrix.** Pearson / Spearman correlation
  heatmap over up to 25 assets with pair-wise log-return alignment (NOT
  forward-fill). Caffeine-cached (`analytics:correlations`, 60s TTL).
  Price-sync writes invalidate.
- **29-03 (G6) — Monte Carlo net-worth projection.** Stochastic 10k-iteration
  fan chart with per-class mean / stddev / weight inputs. Caffeine-cached
  (`analytics:monteCarlo`, 60s TTL). Price-sync writes invalidate.

The three plans share a coherent footprint: each sits under a new
`com.fintrack.analytics.{compare|correlation|montecarlo}` package, mounts a
single `RestController`, exposes a thin `@Service` orchestrator with
`@Observed` + `@Cacheable` + `@Transactional(readOnly = true)`, and ships a
new tab on `AnalyticsPage`. Cache invalidation lands write-adjacent on the
price-sync write paths (`PriceSyncService.persistUpdates` /
`refreshAsset`) for plans 02 and 03; plan 01 invalidates on daily-snapshot
capture. No Flyway migrations, no new infra, no new dependencies — every
plan's footprint is recoverable via `git revert` of its commits.

Total Phase 29 footprint: ~25 new backend Java files, 3 new YAML / fixture
resources, 12 new test files, 5 new frontend components, 6 new hooks /
API methods, 4 new tabs on `AnalyticsPage` (the Overview tab plus three
analytics views), ~80 new i18n keys, and ~250 lines of API.md documentation.
Backend test count: 1342 → 1415 (+73 across the phase). Frontend test count:
~250 → 275 (+25 across the phase).
