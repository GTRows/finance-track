# Codebase Concerns

**Analysis Date:** 2026-05-03

## Tech Debt

**Synchronous `WebClient.block()` in price clients:**
- Files: `backend/src/main/java/com/fintrack/price/client/CoinGeckoClient.java`, `backend/src/main/java/com/fintrack/price/client/TefasClient.java`, `backend/src/main/java/com/fintrack/price/client/ExchangeRateClient.java`, `backend/src/main/java/com/fintrack/price/client/YahooFinanceClient.java`, `backend/src/main/java/com/fintrack/price/client/PreciousMetalsClient.java`
- Why: Reactive WebClient is being used in a blocking style, defeating the non-blocking IO benefit
- Impact: Scheduler thread is held for the duration of each external call; concurrent refreshes serialize
- Fix approach: Switch to async composition (e.g., `Mono.zip` / `Flux.merge`) or move calls to virtual threads (Java 21) and merge results before persisting

**`Thread.sleep` inside transactional fund refresh:**
- File: `backend/src/main/java/com/fintrack/price/PriceSyncService.java` (`refreshFunds`)
- Why: TEFAS lacks a batch endpoint; throttling is implemented with sequential sleeps
- Impact: A DB connection is held for the full loop (~10-15 s for many funds), risking pool exhaustion under load
- Fix approach: Move throttling outside `@Transactional`, fetch all prices first, then persist in a single short transaction; consider virtual threads for parallelism

**Per-instance TEFAS list cache:**
- File: `backend/src/main/java/com/fintrack/price/client/TefasClient.java` (`listCache`, `listCacheAt`)
- Why: In-memory `ConcurrentHashMap` instead of Redis-backed cache
- Impact: Multi-instance deployments would skew cache freshness up to the configured TTL
- Fix approach: Move to Redis with explicit TTL (e.g., 6 h) keyed by fund category

**Full asset broadcast on every price tick:**
- File: `backend/src/main/java/com/fintrack/websocket/PriceBroadcaster.java`
- Why: Reads all assets via `findAllByOrderBySymbolAsc()` and sends the full list
- Impact: WebSocket payload grows linearly with the asset master list (potentially 100+) regardless of which prices changed
- Fix approach: Track changed assets in `PriceSyncService.refreshLive()` and broadcast a delta; add `@Transactional(readOnly = true)` and a query that filters `price IS NOT NULL`

## Known Bugs

**Login error message disappears on rapid reload:**
- Source: `tasks/TODO.md` (Bugs section)
- Symptoms: Wrong-password toast vanishes before the user can read it
- Likely trigger: Race between error UI render and the refresh-then-redirect path in `frontend/src/api/client.ts`
- Fix approach: Skip auto-refresh for `/auth/login` errors; persist the toast until dismissed or for a fixed minimum duration

## Security Considerations

**CORS allows all origins by default:**
- File: `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java`
- Risk: Permissive `allowedOriginPatterns("*")` is acceptable in development but must be tightened for prod
- Current mitigation: Comment in code; intent documented in `docs/SECURITY.md`
- Recommendation: Bind to `CORS_ALLOWED_ORIGINS` env var; production profile fails fast if unset; integration test that rejects wildcard under `production`

**WebSocket endpoint is unauthenticated:**
- Files: `backend/src/main/java/com/fintrack/websocket/`
- Risk: `/topic/prices` is public market data, but the endpoint accepts any client and any origin
- Recommendation: Document the broadcast scope explicitly; if user-specific topics are added, require JWT on the STOMP CONNECT and authorize per-destination; add Nginx-level rate limiting per IP

**Refresh token in `localStorage`:**
- Files: `frontend/src/api/client.ts`, `frontend/src/store/auth.store.ts`
- Risk: Susceptible to XSS exfiltration despite tight CSP and lack of third-party scripts
- Current mitigation: CSP, no user-supplied HTML, refresh rotation, "logout" revokes the row in `users.refresh_tokens`
- Recommendation: Add a "log out all sessions" action; document the trade-off in `docs/SECURITY.md`; evaluate migration to httpOnly cookies in a future hardening pass

**Domain mutations not audited:**
- Files: `backend/src/main/java/com/fintrack/audit/AuditService.java` and feature services in `portfolio/`, `budget/`, `bills/`
- Risk: Auth events are logged, but creates/updates/deletes for portfolios, holdings, budget entries, and bills are not
- Recommendation: Inject `AuditService` into mutating service methods or use Spring AOP to capture writes; reference `docs/THREAT_MODEL.md`

## Performance Bottlenecks

**TEFAS fund refresh latency:**
- File: `backend/src/main/java/com/fintrack/price/PriceSyncService.java`
- Cause: Sequential calls plus throttling for ~100 funds
- Improvement path: Parallelize with virtual threads, then commit once; or shrink frequency if data freshness allows

**Unbounded `findAll` in repositories:**
- Files: per-feature repositories (e.g., audit log, alert, asset)
- Cause: Single-user assumption made pagination optional
- Improvement path: Audit `findAll`/`findByUserId` usages and add pagination on list endpoints (e.g., `GET /api/v1/admin/audit?page=…&size=…`)

## Fragile Areas

**Price scheduler vs. startup refresh overlap:**
- Files: `backend/src/main/java/com/fintrack/scheduler/`, `backend/src/main/java/com/fintrack/price/PriceSyncService.java`
- Risk: A long initial refresh can collide with the 30 s live tick
- Mitigation: `try/catch` around scheduler invocations swallows errors; prices stale rather than crashing
- Recommendation: Add `@SchedulerLock` (ShedLock) or guard with an in-process lock; surface skipped runs as metrics
- Diagnosability: 26-01 added OpenTelemetry trace IDs to MDC and emits `price.refresh.live` spans to Tempo; overlapping ticks now show up as two distinct trace trees in Grafana Explore for visual confirmation. Still requires `@SchedulerLock` to actually fix the race.

**WebSocket reconnect after token rotation:**
- Files: `frontend/src/lib/stompClient.ts`, `frontend/src/hooks/useLivePrices.ts`
- Risk: After a successful access-token refresh, the existing STOMP connection may still carry the old credentials if/when authentication is added on the WS endpoint; today this is a no-op because `/topic/prices` is public, but it will matter once user-specific topics ship
- Recommendation: Reconnect the STOMP client on auth state change

**Email delivery without circuit breaker:**
- Files: `backend/src/main/java/com/fintrack/notification/`, `backend/src/main/java/com/fintrack/report/`
- Risk: SMTP outages could cause backed-up retries
- Recommendation: Add explicit retry with backoff; configure `spring.mail.properties.mail.smtp.timeout`; surface failures via metrics

## Scaling Limits

**Single-tenant assumption baked into the schema:**
- Scope: Entities filter by `userId`; no `tenant_id` column anywhere
- Recommendation: Document as an intentional scope boundary in `docs/ARCHITECTURE.md`; multi-user/family support would need schema and service-layer audits

**Redis password optional in default config:**
- Files: `docker-compose.yml`, `.env.example`
- Risk: Misconfigured production deployments could expose Redis without auth
- Recommendation: Fail backend startup in `production` profile when `SPRING_REDIS_PASSWORD` is missing

## Dependencies at Risk

**Spring Boot 3.2.4:**
- File: `backend/pom.xml`
- Status: Maintained but trailing the 3.3/3.4 lines
- Recommendation: Plan a controlled bump within the next minor release window

**Apache POI 5.2.5:**
- File: `backend/pom.xml`
- Risk: Historic CVEs; review against current advisories before processing untrusted Excel inputs
- Recommendation: Run `mvn dependency-check` (or similar SCA) and upgrade to 5.3.x if affected

**Frontend libs (React 18.2, Vite 5.1, TS 5.4):**
- Status: Current and stable
- Recommendation: Enable Renovate or Dependabot patch auto-merge to keep up

## Missing Critical Features

**Domain audit logging** — see Security Considerations.
**Bound email-verify / password-reset tokens to session context** — `docs/THREAT_MODEL.md` notes this as residual risk; TOTP enrollment mitigates today.
**Backlog items in `tasks/TODO.md`** — should be cross-referenced when planning the next phases.

## Test Coverage Gaps

**Backend:**
- `PriceSyncService` end-to-end refresh path
- Multi-currency transaction flows with FX conversion
- Audit-log append-only invariants
- Concurrency edges in the login rate limiter
- Recommendation: Add Testcontainers-backed integration tests per price client and one full sync test

**Frontend:**
- No end-to-end suite (Playwright not committed)
- Visual regression on charts (allocation, history) with edge data
- Locale-sensitive formatting (TR vs en-US decimals)
- Mobile breakpoints below 375 px

## Documentation Drift

- Verify `.env.example` lists every variable read by the backend (CORS, push VAPID, Redis password, SMTP, RESTIC_*, etc.)
- `docs/API.md` should include the WebSocket frame format and topics
- `docs/SECURITY.md` should explicitly enumerate the JWT claims used by the project

---

*Concerns audit: 2026-05-03*
*Update as issues are fixed or new ones discovered*
