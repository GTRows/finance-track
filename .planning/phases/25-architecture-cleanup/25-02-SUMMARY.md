---
phase: 25-architecture-cleanup
plan: 02
subsystem: architecture
tags: [cache, caffeine, spring-cache, hot-reads]

requires:
  - phase: 24-security-hardening
    provides: AuditService coverage on category mutations (24-08)
  - phase: 25-architecture-cleanup
    plan: 01
    provides: ApplicationEventPublisher boundary (25-01) — referenced for future listener-based invalidation, not consumed in 25-02

provides:
  - Caffeine 3.1.8 + spring-boot-starter-cache wired as the Spring Cache provider via SimpleCacheManager
  - Three named caches with bespoke specs (assets: 1h expire-after-write, max 200; userSettings: 30m expire-after-access, max 16; categoryLookup: 30m expire-after-access, max 16)
  - AssetService extracted as a Spring proxy boundary so @Cacheable on listAll / findById is reachable from AssetController
  - @Cacheable on three hot reads (AssetService.listAll, AssetService.findById, SettingsService.get, CategoryService.listAll)
  - @CachePut on two settings write methods (update, markOnboardingComplete) so the canonical response is written through to the cache
  - @CacheEvict on seven write methods (TefasFundService.importFund, six CategoryService write methods)
  - Removal of dead spring.cache.type: redis line from application.yml (zero @Cacheable consumers were ever pointed at Redis)

affects: [26-01, 30-01, 30-02]

tech-stack:
  added:
    - caffeine 3.1.8
    - spring-boot-starter-cache (explicit, version-managed by Spring Boot 3.2.4)
  patterns:
    - "Cache key strategy: type-keyed entries use the enum name (or 'ALL') and by-id entries are prefixed with 'ID:' to share a single cache namespace without collision."
    - "AssetService-as-cache-proxy-boundary: @Cacheable on a Spring Data interface method is fragile (proxy ordering footgun); the safe pattern is a @Service wrapper method called through the cache proxy."
    - "@CachePut for canonical write responses: when a write method already returns the persisted entity-as-DTO, @CachePut writes that response into the cache so the next reader does not re-query the DB."
    - "@CacheEvict for multi-write invalidations: when six writers mutate one logical view, evicting the user's cache entry on each is simpler than computing the precise key set."
    - "@CacheEvict default beforeInvocation=false: failed writes (ResourceNotFoundException on update / delete with bad id) do NOT evict — cache stays consistent with the DB."

key-files:
  created:
    - backend/src/main/java/com/fintrack/common/config/CacheConfig.java
    - backend/src/main/java/com/fintrack/asset/AssetService.java
    - backend/src/test/java/com/fintrack/common/config/CacheConfigTest.java
    - backend/src/test/java/com/fintrack/asset/AssetServiceTest.java
    - backend/src/test/java/com/fintrack/asset/AssetServiceCacheTest.java
    - backend/src/test/java/com/fintrack/settings/SettingsServiceCacheTest.java
    - backend/src/test/java/com/fintrack/budget/CategoryServiceCacheTest.java
  modified:
    - backend/pom.xml
    - backend/src/main/resources/application.yml
    - backend/src/main/java/com/fintrack/asset/AssetController.java
    - backend/src/main/java/com/fintrack/asset/TefasFundService.java
    - backend/src/main/java/com/fintrack/settings/SettingsService.java
    - backend/src/main/java/com/fintrack/budget/CategoryService.java
    - backend/src/test/java/com/fintrack/asset/AssetControllerWebMvcTest.java

key-decisions:
  - "Caffeine is in-process; eviction stays on the writer's call stack. 25-01's ApplicationEventPublisher is NOT used for cache invalidation. Future plans that move to a multi-instance or Redis cache will route eviction through 25-01's event listeners; the @CacheEvict annotations in 25-02 will then translate to listener calls."
  - "AssetController called AssetRepository directly. @Cacheable on a Spring Data interface method is fragile (cache proxy / repo proxy AOP advisor ordering becomes a footgun); introducing AssetService is the smallest blast-radius proxy boundary. The history endpoint stays on the repository because the metadata path needs the entity (Asset.getMetadata()), not the DTO."
  - "@CacheEvict default beforeInvocation=false means a failed update keeps the cache consistent with the DB. SettingsService and CategoryService both rely on this; pinned by the negative test in CategoryServiceCacheTest.failedUpdateDoesNotEvictCache."
  - "@CachePut on SettingsService write methods (not @CacheEvict) is strictly faster: the response is canonical, so writing it through saves a re-read on the next get. Pinned by SettingsServiceCacheTest.updateWritesFreshValueIntoCacheWithoutExtraReadOnNextGet."
  - "AuthService.register(...) creates the initial UserSettings row but is OUT OF SCOPE: the cache is empty at registration time, so the next GET returns a miss and reloads from DB. No additional annotation needed."
  - "TefasFundService.importFund evicts allEntries=true on the assets cache. Importing one fund could change the by-type list (FUND), the all-assets list, AND the by-id entry; clearing the entire small cache is simpler and cheaper than enumerating keys."
  - "PriceBroadcaster, SavingsGoalService, FireService, and other internal callers continue to call AssetRepository directly. Routing scheduled / write-adjacent reads through the cache would either serve stale prices or widen the eviction surface — out of scope for 25-02."

patterns-established:
  - "com.fintrack.common.config.CacheConfig as the single source of truth for named caches and per-cache Caffeine specs. Future named caches add a constant + a CaffeineCache bean here; do NOT scatter @CacheConfig stubs across feature packages."
  - "Per-feature *ServiceCacheTest pattern: @SpringJUnitConfig with a static @EnableCaching @Import({CacheConfig.class, ServiceUnderTest.class}) inner class, @MockBean on the repository / collaborator, @BeforeEach cache.clear(). Pins the second-call-no-DB-hit invariant that's invisible to the unit @ExtendWith(MockitoExtension.class) test."
  - "Existing *ServiceTest unit tests stay unchanged — annotations are invisible without a Spring proxy. The cache contract lives in the dedicated *ServiceCacheTest sibling."

duration: 8 min
completed: 2026-05-07
---

# Phase 25 Plan 02: Spring Cache + Caffeine on Hot Reads

**Asset master, user settings, and category lookup reads are served from a process-local Caffeine cache; every write that mutates the cached state evicts (or writes through) the entry deterministically.**

## Performance

- **Duration:** ~8 min (subagent execution)
- **Tasks:** 3 (sequential, atomic per-task commits)
- **Files added:** 7 (1 config, 1 service, 5 tests)
- **Files modified:** 7 (pom + yml + 3 service classes + 1 controller + 1 web-mvc test)

## Accomplishments

- Caffeine 3.1.8 + `spring-boot-starter-cache` wired in `pom.xml`; the dead `spring.cache.type: redis` line in `application.yml` (zero `@Cacheable` consumers ever) replaced with `caffeine` + explicit `cache-names`.
- `com.fintrack.common.config.CacheConfig` defines a `SimpleCacheManager` with three explicitly specced `CaffeineCache` beans: `assets` (1h expire-after-write, max 200), `userSettings` (30m expire-after-access, max 16), `categoryLookup` (30m expire-after-access, max 16). `recordStats()` is on so a future Phase 26 (observability) can expose hit/miss counts via Micrometer without re-touching this file.
- `AssetService` extracted; `AssetController.list` / `get` route through it; `AssetController.history` keeps direct repository access for the metadata path (entity-touching, out of cache scope).
- `TefasFundService.importFund(...)` annotated with `@CacheEvict(allEntries = true)` on the `assets` cache.
- `SettingsService.get(...)` annotated with `@Cacheable`; `update(...)` and `markOnboardingComplete(...)` annotated with `@CachePut` so the canonical response is written through.
- `CategoryService.listAll(...)` annotated with `@Cacheable`; the six write methods (`createIncome`, `createExpense`, `updateIncome`, `updateExpense`, `deleteIncome`, `deleteExpense`) each annotated with `@CacheEvict` keyed on `userId`.
- 24-08 audit emission contract preserved end-to-end: action constants unchanged, userId unchanged, id-substring detail unchanged, post-write ordering unchanged. Every `*ServiceAuditTest` assertion still passes.
- `cd backend && ./mvnw -B -ntp verify` is green: **1054 tests, 0 failures, 0 errors, 132 skipped** (Testcontainers-bound tests skip without Docker, expected). JaCoCo gates: "All coverage checks have been met." Spotless clean.
- Annotation count tally matches the plan exactly: **4 `@Cacheable`, 7 `@CacheEvict`, 2 `@CachePut` across 4 source files**.

## Task Commits

| # | Task | Type | Hash |
|---|------|------|------|
| 1 | Caffeine + CacheConfig + assets cache (AssetService extraction, AssetController switch, TefasFundService eviction) + 4 new tests | feat | ddb1665 |
| 2 | User settings cache — @Cacheable on get, @CachePut on update / markOnboardingComplete + 1 new test | feat | 5e2ed5f |
| 3 | Category lookup cache — @Cacheable on listAll, @CacheEvict on six write methods + 1 new test + plan SUMMARY | feat | (this commit) |

## Files Created/Modified

### Created (7)

**Config (1)**
- `backend/src/main/java/com/fintrack/common/config/CacheConfig.java`

**Service (1)**
- `backend/src/main/java/com/fintrack/asset/AssetService.java`

**Tests (5)**
- `backend/src/test/java/com/fintrack/common/config/CacheConfigTest.java` — 3 tests (bean wiring, three-named-caches, all-Caffeine-backed).
- `backend/src/test/java/com/fintrack/asset/AssetServiceTest.java` — 5 unit tests on the service body (annotations invisible without Spring proxy).
- `backend/src/test/java/com/fintrack/asset/AssetServiceCacheTest.java` — 6 Spring-context tests pinning second-call-no-DB-hit, by-type-cached-independently, by-id-cached-independently, clear-forces-reload, empty-Optional-cached.
- `backend/src/test/java/com/fintrack/settings/SettingsServiceCacheTest.java` — 5 Spring-context tests covering get-cached, distinct-users-distinct-entries, update-CachePut, markOnboardingComplete-CachePut, ResourceNotFoundException-not-cached.
- `backend/src/test/java/com/fintrack/budget/CategoryServiceCacheTest.java` — 9 Spring-context tests covering listAll-cached, distinct-users-distinct-entries, six write-methods-evict, failed-update-does-not-evict.

### Modified (7)

**Build / config (2)**
- `backend/pom.xml` — added `spring-boot-starter-cache` and `com.github.ben-manes.caffeine:caffeine:3.1.8`.
- `backend/src/main/resources/application.yml` — replaced `spring.cache.type: redis` (with stale `redis.time-to-live`) with `spring.cache.type: caffeine` + `cache-names: assets,userSettings,categoryLookup`.

**Source (4)**
- `backend/src/main/java/com/fintrack/asset/AssetController.java` — `assetService` injected; `list` / `get` delegate to the service; `history` keeps direct `assetRepository` access for the metadata path.
- `backend/src/main/java/com/fintrack/asset/TefasFundService.java` — `@CacheEvict(value = ASSETS_CACHE, allEntries = true)` on `importFund(...)`.
- `backend/src/main/java/com/fintrack/settings/SettingsService.java` — `@Cacheable` on `get`, `@CachePut` on `update` and `markOnboardingComplete`.
- `backend/src/main/java/com/fintrack/budget/CategoryService.java` — `@Cacheable` on `listAll`, `@CacheEvict` on the six write methods.

**Tests (1)**
- `backend/src/test/java/com/fintrack/asset/AssetControllerWebMvcTest.java` — `@MockBean AssetService` added; `list` / `get` / `getReturns404WhenMissing` test cases now mock the service. The `history` test keeps the `assetRepository` mock.

## Decisions Made

- **Listener-based invalidation deferred (top-level decision).** Caffeine is in-process; an `@CacheEvict` on the writer method runs inside the writer's call stack, before any 25-01 listener fires. Listener-based invalidation only matters when the cache is multi-instance (Redis-backed). 25-01's `ApplicationEventPublisher` is intentionally NOT consumed in 25-02. Future plans that move to a multi-instance or Redis-backed cache will route eviction through 25-01's event listeners; the `@CacheEvict` annotations here will translate to listener calls.
- **AssetService extracted as a proxy boundary.** `AssetController` called `AssetRepository` directly. `@Cacheable` on a Spring Data interface method is fragile (the cache advisor and the Spring Data repository advisor collide; AOP advisor ordering becomes a footgun). Introducing `AssetService` is the smallest blast-radius proxy boundary. The history endpoint stays on the repository because the metadata path reads the entity (`Asset.getMetadata()`), not the DTO.
- **`@CachePut` for settings writes (not `@CacheEvict`).** The `update(...)` and `markOnboardingComplete(...)` methods both return the canonical persisted-entity-as-DTO. `@CachePut` writes that response into the cache so the next `get(...)` returns the cached fresh value without re-querying the DB. Strictly faster than `@CacheEvict`, which would force a re-read on the next call.
- **`@CacheEvict` default `beforeInvocation = false`.** Failed writes (e.g., `ResourceNotFoundException` on update / delete with bad id) do NOT evict — cache stays consistent with the DB. Pinned by `CategoryServiceCacheTest.failedUpdateDoesNotEvictCache`.
- **`AuthService.register(...)` is out of scope.** It writes the initial `UserSettings` row at registration time, when the cache for that user is empty. The next `GET /settings` returns a miss naturally and reloads from the DB. No additional annotation needed.
- **`importFund` evicts `allEntries=true` on the assets cache.** Importing one fund could change the by-type (`FUND`) list, the all-assets list, and the by-id entry simultaneously. Clearing the entire small cache (max 200 entries) is simpler and cheaper than enumerating the precise key set. Imports are user-driven and rare.
- **Category writes use `@CacheEvict`, not `@CachePut`.** The category cache stores a `CategoriesResponse` (income + expense lists). The six write methods each return a single new / updated category, which is not the full response shape. `@CachePut` is not the right primitive here; `@CacheEvict` followed by a re-read on the next listAll is the correct contract.
- **`recordStats()` is on for all three caches.** Phase 26's observability work can wire Caffeine stats through Micrometer (`CaffeineStatsCounter`) without re-touching this file.

## Mutation Coverage Results

The pitest pass was not run as part of this plan (carry-over flake from 24-04 / 24-08 on JDK 21 + Windows). The verify suite is the hard gate and it is green; per-class kill rates can be sampled in a follow-up. Expected behaviour after this plan:

- `@Cacheable` / `@CacheEvict` / `@CachePut` annotations do not change the method body. Existing service-level mutation kill rates carry over unchanged.
- `AssetService` is a thin proxy over `AssetRepository`; its mutation kill rate is bounded by the `AssetServiceTest` (5 unit tests) plus the upstream coverage of `AssetRepository` from `AssetRepositoryDataJpaTest`. The cache annotations themselves are not mutated.
- `SettingsService` and `CategoryService` keep their pre-25-02 kill rates: the existing 9 / 13 unit tests still cover every method body byte-for-byte.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Cosmetic] `verifyNoInteractions()` no-arg form in AssetServiceTest**
- **Found during:** Task 1 first verify run.
- **Issue:** The plan's test sketch had a `verifyNoInteractions()` no-arg call in `listAllReturnsEmptyWhenRepositoryEmpty`. Mockito requires at least one mock argument.
- **Fix:** Removed the line — the test still pins the empty-repo behaviour via the assertion on the empty list.
- **Committed in:** ddb1665.

### Deferred Enhancements

- **`BudgetRuleService.list*` and `WatchlistService.list(...)` are credible cache candidates** that the plan explicitly defers. Same shape (per-user, low-medium frequency, evict-on-write). The category-cache evict-on-write surface in 25-02 already handles the FK cascade case for budget rules implicitly; a 25-02 follow-up plan could route those reads through `@Cacheable` and add `@CacheEvict` to the matching writers.
- **Pagination on `findAll`-style repository methods** (CONCERNS.md "Unbounded `findAll`") is partially addressed: the asset list `findAll` now goes through the cache, so the unbounded read fires at most once per TTL window per process. Real pagination is still the proper long-term fix, not in scope here.
- **TEFAS list cache** (CONCERNS.md "Per-instance TEFAS list cache") stays as a separate concern for a future plan. Plan 25-02's C2 target is the persisted asset master, not the upstream TEFAS catalog.

---

**Total deviations:** 1 auto-fixed (Rule 1), 0 deferred from scope, 3 deferred enhancements logged for future plans.
**Impact on plan:** Behaviour preserved end-to-end. Tests green.

## Issues Encountered

- One Mockito API mistake (`verifyNoInteractions()` no-arg form) caught by the first targeted test run; fixed inline before commit.
- Spotless reformatted seven files on first apply (long `@Cacheable` / `@CacheEvict` / `@CachePut` line wraps and one Javadoc reflow). Standard for this repo.
- Initial test failure surface was 1/29 in Task 1; once corrected, all three tasks ran clean on first verify.

## Next Phase Readiness

- Plan 25-03 (de-block reactive price clients) is the next plan; nothing in 25-02 blocks it. The cache layer is invisible to the price-sync path because `PriceSyncService` reads `AssetRepository` directly (not via `AssetService`), preserving freshness for scheduled work. The `AssetController.history` endpoint also bypasses the cache — it owns its own upstream client routing, which 25-03 will refactor.
- Phase 26 (observability) can wire Caffeine stats through Micrometer; `recordStats()` is already on across the three caches in `CacheConfig`.
- The `ApplicationEventPublisher` boundary from 25-01 stays unconsumed in 25-02. A future plan that moves to a multi-instance / Redis-backed cache will route eviction through that boundary; the current `@CacheEvict` annotations are the natural anchor points for that translation.

---
*Phase: 25-architecture-cleanup*
*Completed: 2026-05-07*
