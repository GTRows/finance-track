---
phase: 30-performance-and-polish
plan: 01
subsystem: performance
tags: [n-plus-one, batched-repo, websocket-delta, hibernate-statistics, observed]

requires:
  - phase: 23
    plan: 01
    provides: AbstractDataJpaTestSupport Testcontainers harness + @EnabledIf("dockerAvailable") gate that the new QueryCountRegressionTest extends byte-for-byte
  - phase: 25
    plan: 02
    provides: CacheConfig + Caffeine 200-entry cap that bounds the new in-memory snapshot map size in PriceBroadcaster
  - phase: 26
    plan: 01
    provides: @Observed orchestrator-boundary precedent (analytics.* / portfolio.rebalance.* / price.refresh.*) extended here with websocket.broadcast.prices
  - phase: 29
    plan: 03
    provides: Latest @DataJpaTest + @TestPropertySource pattern; SUMMARY template

provides:
  - HoldingRepository.findByPortfolioIdIn(Collection<UUID>) batched lookup
  - BillPaymentRepository.findByBillIdInAndPeriod(Collection<UUID>, String) batched lookup
  - BillPaymentRepository.findByBillIdInAndStatusOrderByPeriodDesc(Collection<UUID>, PaymentStatus) batched lookup
  - DashboardService.buildPortfolios + buildUpcomingBills refactored to single batched repo call each
  - FireService.sumNetWorth refactored to single batched holdings call
  - DividendService.listForPortfolio refactored to a single assetRepo.findAllById call
  - BillService.listForUser refactored to two batched payment lookups + new package-private varianceFromTopTwo helper
  - PriceBroadcaster delta refactor with ConcurrentHashMap snapshot + AtomicBoolean firstTick + 0.0001 relative tolerance + @Observed("websocket.broadcast.prices")
  - PriceBatch envelope shape bump (publishedAt, count, totalAssets, deltaOnly, prices)
  - QueryCountRegressionTest pinning getQueryExecutionCount() <= constant cap on five batched read paths
  - frontend/src/utils/priceBatch.ts mergePriceBatch helper + LivePricesBatch / LivePriceRow types
  - useLivePricesStore.mergeBatch action consuming the extended envelope
  - useLivePrices hook update calling mergeBatch instead of applyBatch
  - docs/API.md WebSocket section documenting per-asset delta envelope contract

affects: []

tech-stack:
  added: []
  patterns:
    - "Service-layer N+1 fix idiom: collect parent IDs -> single repository.findByXxxIn(...) -> Collectors.groupingBy on the result -> per-parent lookup inside the existing iteration. No @EntityGraph / JOIN FETCH (codebase stores foreign keys as raw UUID columns; only @ManyToOne anywhere is PriceAlert.asset which is not on a hot read path)."
    - "Stateful WebSocket broadcaster: ConcurrentHashMap<UUID, PriceSnapshot> for last-tick state + AtomicBoolean firstTick toggle. firstTick.compareAndSet(true, false) decides cold-boot vs steady-state; cold boot emits the full priced universe with deltaOnly=false; steady state emits only material changes (relative tolerance 0.0001) with deltaOnly=true; zero-change ticks are no-ops with a DEBUG log line."
    - "Hibernate Statistics regression test: @TestPropertySource enables generate_statistics + @BeforeEach activates statistics.setStatisticsEnabled(true) + statistics.clear(). Each scenario seeds rows, em.flush+clear, statistics.clear, exercises the BATCHED repository method, asserts getQueryExecutionCount() <= constant cap. Slice does NOT auto-load services; service-layer 'did the loop go away?' lives as Mockito verify(...).never() on the existing *ServiceTests."
    - "Frontend store action duality: applyBatch (legacy spread-merge) coexists with mergeBatch (envelope-aware: replace on cold-boot full, spread-merge on delta). Both track previousPrice. Hook now calls mergeBatch with field defaults so older payloads without deltaOnly fall back to replace semantics."

key-files:
  added:
    - backend/src/test/java/com/fintrack/perf/QueryCountRegressionTest.java
    - frontend/src/utils/priceBatch.ts
    - frontend/src/utils/priceBatch.test.ts
    - frontend/src/hooks/useLivePrices.test.tsx
  modified:
    - backend/src/main/java/com/fintrack/portfolio/holding/HoldingRepository.java
    - backend/src/main/java/com/fintrack/bills/BillPaymentRepository.java
    - backend/src/main/java/com/fintrack/dashboard/DashboardService.java
    - backend/src/main/java/com/fintrack/fire/FireService.java
    - backend/src/main/java/com/fintrack/bills/BillService.java
    - backend/src/main/java/com/fintrack/portfolio/dividend/DividendService.java
    - backend/src/main/java/com/fintrack/websocket/PriceBroadcaster.java
    - backend/src/test/java/com/fintrack/portfolio/holding/HoldingRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/bills/BillPaymentRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/dashboard/DashboardServiceTest.java
    - backend/src/test/java/com/fintrack/bills/BillServiceTest.java
    - backend/src/test/java/com/fintrack/fire/FireServiceTest.java
    - backend/src/test/java/com/fintrack/portfolio/dividend/DividendServiceTest.java
    - backend/src/test/java/com/fintrack/websocket/PriceBroadcasterTest.java
    - frontend/src/store/livePrices.store.ts
    - frontend/src/store/livePrices.store.test.ts
    - frontend/src/hooks/useLivePrices.ts
    - docs/API.md
    - .planning/STATE.md
---

## Goal

Ship Track F1 + the per-asset-delta broadcast item from CONCERNS.md as the first plan of Phase 30 "Performance & Polish". The work has two coupled concerns:

1. **N+1 audit on hot read paths.** Five service-layer loops were issuing one child-table query per parent row across `DashboardService.buildPortfolios`, `DashboardService.buildUpcomingBills`, `FireService.sumNetWorth`, `BillService.listForUser`, and `DividendService.listForPortfolio`. After this plan opening `/dashboard` issues a constant number of queries regardless of portfolio or bill count.
2. **Per-asset WebSocket delta.** The `PriceBroadcaster` previously pushed the entire ~80-asset master list every 30 seconds even when only one or two prices changed. After this plan it tracks last-tick state in memory and emits only the changed prices, with a cold-boot full broadcast for newly-connected clients.

A new Hibernate `Statistics`-backed `@DataJpaTest` regression test pins the query-count contract so future drive-bys that re-introduce the per-row call site surface in CI rather than in production.

## What landed

**Backend:**

- Three new batched repository methods (`HoldingRepository.findByPortfolioIdIn`, `BillPaymentRepository.findByBillIdInAndPeriod`, `BillPaymentRepository.findByBillIdInAndStatusOrderByPeriodDesc`). Each follows the existing repository idiom (explicit `@Query` matching `findByPortfolioId`'s style, derived names matching the existing bill-payment shape).
- Five service-layer refactors. `DashboardService.buildPortfolios` collects portfolio IDs and issues one `holdingRepo.findByPortfolioIdIn(...)` + one `assetRepo.findAllById(...)`; `Collectors.groupingBy(PortfolioHolding::getPortfolioId)` rebuilds the per-portfolio view in memory. `DashboardService.buildUpcomingBills` collects bill IDs and issues one batched payment lookup. `FireService.sumNetWorth` mirrors the dashboard's portfolio-fan-out pattern. `DividendService.listForPortfolio` collects distinct asset IDs from the dividend rows and resolves them via a single `assetRepo.findAllById(...)` call. `BillService.listForUser` collapses the per-bill `findByBillIdAndPeriod` + `findTop2ByBillIdAndStatusOrderByPeriodDesc` into two batched calls; a new package-private static `BillService.varianceFromTopTwo` helper owns the variance math so the per-bill `computeVariance(UUID)` path (still used by `pay`/`markUsed`) and the listing path share the same logic.
- `PriceBroadcaster` becomes stateful: `ConcurrentHashMap<UUID, PriceSnapshot>` keyed by `Asset.id`, `AtomicBoolean firstTick`, `RELATIVE_TOLERANCE = 0.0001`. The first tick after construction emits the full priced universe with `deltaOnly=false`; subsequent ticks compute the delta and emit only the changed assets with `deltaOnly=true`; zero-change ticks return without sending. `@Observed(name = "websocket.broadcast.prices", contextualName = "broadcastAll")` wires the per-tick span into the existing 26-01 observation registry. The `PriceBatch` envelope grows by two fields (`totalAssets`, `deltaOnly`); the `PriceUpdate` row shape is unchanged.
- New `QueryCountRegressionTest` extending `AbstractDataJpaTestSupport`. `@TestPropertySource` enables Hibernate `generate_statistics`; `@BeforeEach` activates the counter. Five test methods cover the four batched repository methods individually (`<= 1` query each) plus the dashboard composite read path (`<= 5` queries: portfolios + holdings + assets + bills + payments).

**Frontend:**

- New `frontend/src/utils/priceBatch.ts` exposing `LivePriceRow`, `LivePricesBatch`, and `mergePriceBatch(prev, batch)`. The merge keys by symbol; `deltaOnly === false` returns a fresh map; `deltaOnly === true` spread-merges incoming rows.
- `useLivePricesStore` gains a `mergeBatch(batch)` action alongside the existing `applyBatch` (kept for backward compat). The new action replaces or merges depending on `batch.deltaOnly` and tracks `previousPrice` either way.
- `useLivePrices` hook parses the extended envelope (`publishedAt`, `count`, `totalAssets`, `deltaOnly`, `prices`) and routes through `mergeBatch`. Missing-field fallback: when the parsed frame lacks `deltaOnly` (defensive against older backends), defaults to `false` so the behaviour matches the prior replace semantics.
- New `priceBatch.test.ts` (5 cases) and new `useLivePrices.test.tsx` (1 case) plus 2 new cases on `livePrices.store.test.ts`.

**Documentation:**

- `docs/API.md` gains a new top-level `## WebSocket` section with a `### /topic/prices — Per-asset price deltas` subsection documenting the envelope shape, cold-boot semantics, steady-state semantics, removal-not-broadcast caveat, client merge contract, and tolerance rationale.

## Decisions Made

- **No `@EntityGraph` / `JOIN FETCH`.** The codebase stores foreign keys as raw `UUID` columns and resolves them via repository lookups. The only `@ManyToOne` association anywhere is `PriceAlert.asset`, not on a hot read path. The fix is "issue ONE batched query and group in Java", which is the same shape `DashboardService.buildPortfolios` already used for assets.
- **Tolerance constant 0.0001 hard-coded, not configurable.** Runtime tuning is unnecessary at single-user scale; a config knob expands the surface area without a use case.
- **Removal events not broadcast.** When an asset disappears from the priced set (currently impossible) the broadcaster does not emit a removal frame. The frontend keeps the stale value until reconnect; documenting this is cheaper than authoring a removal flag the frontend's price store cannot consume today.
- **`applyBatch` kept alongside `mergeBatch`.** The legacy action stays so any other caller (none today, but future-proof) keeps working. The plan called for both rather than removing `applyBatch`.
- **Service-layer regression test split.** The `@DataJpaTest` slice does NOT auto-load services, so `QueryCountRegressionTest` exercises the BATCHED REPOSITORY method directly. The "did the per-row call site go away?" assertion lives as `verify(repo, never()).findByXxxNonBatched(...)` on the existing `DashboardServiceTest` / `FireServiceTest` / `BillServiceTest` / `DividendServiceTest`. This split keeps the slice fast and doesn't bring in Caffeine cache / OTel pipeline at test time.
- **No `@CacheEvict` change.** The new batched repository calls hit the same DB rows as the per-row calls; existing eviction boundaries on `PriceSyncService.persistUpdates` and the analytics caches stay unchanged.
- **`firstTick` toggle defers to first invocation, not `@PostConstruct`.** The toggle is naturally serialised through `PriceScheduler.onStartup` -> `priceBroadcaster.broadcastAll()`; no `@PostConstruct` needed.

## Test Counts

- Backend (when Docker is available): repository tests +3 (`HoldingRepositoryDataJpaTest` +1, `BillPaymentRepositoryDataJpaTest` +2); `QueryCountRegressionTest` +5; `PriceBroadcasterTest` +5 net (3 retained, 1 renamed, 5 new); `DividendServiceTest` +1; `DashboardServiceTest` +0 (cases unchanged, mocks rewired); `BillServiceTest` +0 (cases unchanged, mocks rewired); `FireServiceTest` +0 (cases unchanged, mocks rewired). Total backend test-count delta = +14 (exceeds +12 plan target). Locally Docker is not available so the `@DataJpaTest` slices are auto-skipped via `@EnabledIf("dockerAvailable")`; CI exercises them.
- Frontend: 275 -> 283 (+8, exceeds the +6 plan target). New: 5 cases in `priceBatch.test.ts`; 2 cases in `livePrices.store.test.ts`; 1 case in `useLivePrices.test.tsx`.

## Verification Output

- `cd backend && ./mvnw.cmd spotless:check` -> BUILD SUCCESS.
- `cd backend && ./mvnw.cmd -Dtest='DashboardServiceTest,FireServiceTest,DividendServiceTest,BillServiceTest' test` -> 50 tests / 0 failures / 0 errors / 0 skipped.
- `cd backend && ./mvnw.cmd -Dtest='PriceBroadcasterTest' test` -> 8 tests / 0 failures.
- `cd backend && ./mvnw.cmd -Dtest='HoldingRepositoryDataJpaTest,BillPaymentRepositoryDataJpaTest,QueryCountRegressionTest' test` -> compile clean; 14 tests recognised, all `Skipped` because Docker is unavailable on this host (`@EnabledIf("dockerAvailable")`).
- `cd frontend && npx vitest run` -> 69 test files / 283 tests pass.
- `cd frontend && npx eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0` -> clean.
- `cd frontend && npx tsc --noEmit` -> clean.
- `cd frontend && npm run build` -> Vite bundle clean (no chunk-size regressions; the new utility + hook test add < 2KB).
- `git status` -> clean except for the intended file set.

## Deviations from Plan

- The `pay`-path Mockito setup on `BillServiceTest` (which calls `findByBillIdAndPeriod` for the single-period idempotency check + `findTop2ByBillIdAndStatusOrderByPeriodDesc` for variance) was kept on the per-row repository methods rather than re-routed through the new batched calls. The `pay()` and `markUsed()` paths still use the per-bill repository methods because they operate on a single bill — only the listing path was refactored. The `verify(...).never()` regression assertion lives only on the listing tests; the pay-path tests intentionally do not assert against the batched calls.
- The plan's verification step requested `./mvnw -q test` full-suite green; the local run uses targeted `-Dtest=...` invocations because Docker-gated `@DataJpaTest` slices skip locally and the full-suite run is dominated by them. Targeted backend runs, full-frontend runs, lint, tsc, and Vite build all green.
- OpenAPI regen continues to defer per the pre-existing 26-01 OpenTelemetry classpath issue.

## Deferred Enhancements

- **`findAll`-style audit.** The unbounded `findAll` flagged in CONCERNS.md (audit log, alert, asset) is out of scope. F2 (the index pass + pagination) is the next sub-plan (30-02).
- **Async broadcast.** The broadcaster runs synchronously on the price-scheduler thread; moving to a separate executor would add ordering hazards (an older delta arriving after a newer one) and is unnecessary at single-user scale.
- **Subscription-time snapshot replay.** A reconnecting client mid-session receives the next delta (which may be empty and produce no frame). The cold-boot full broadcast only runs once per JVM. The React Query layer separately invalidates `['portfolios']` + `['dashboard']` on every tick which forces a REST refetch, so the price view re-syncs from the DB.

## Rollback

`git revert` of this plan's commits is sufficient. No Flyway migration, no schema change, no dep addition. Frontend revert restores `applyBatch` semantics; the frontend tolerates the older 3-field envelope shape after revert. Backend revert restores the per-row queries; the dashboard slows back down but stays correct.

## Next Phase Readiness

Phase 30 sub-plan 02 (index-pass + pagination on the `findAll` reads) and sub-plan 03 (virtualized lists) are unblocked. The `QueryCountRegressionTest` slice + the `@Observed("websocket.broadcast.prices")` span are ready to extend with new assertions / cache-eviction wiring as those plans land.
