# Architecture

**Analysis Date:** 2026-05-03

## Pattern Overview

**Overall:** Feature-modular full-stack monolith. One Spring Boot backend, one React SPA, one PostgreSQL database, one Redis cache, fronted by Nginx. Single-tenant (one owner user).

**Key characteristics:**
- Each backend feature is a vertical slice (controller + service(s) + repository + DTOs in the same package)
- DTOs are immutable Java records
- Stateless HTTP via JWT; access (15 min) + refresh (30 d) with rotation
- Real-time price feed pushed over STOMP/WebSocket
- Schema is Flyway-managed; Hibernate runs in `validate` mode
- Server state on the frontend lives in React Query; ephemeral UI state in Zustand

## Layers

**HTTP / Edge:**
- Purpose: TLS termination, rate limiting, header injection, static frontend
- Where: `nginx/nginx.conf`, `nginx/conf.d/`, `nginx/snippets/`
- Used by: Browser
- Forwards `/api/*` to Spring Boot, `/ws` to the WebSocket endpoint, everything else to the static frontend

**Controller:**
- Purpose: Bind HTTP, validate input, return `ResponseEntity<T>`
- Examples: `backend/src/main/java/com/fintrack/auth/AuthController.java`, `backend/src/main/java/com/fintrack/portfolio/PortfolioController.java`
- Depends on: Service layer, validation, `@AuthenticationPrincipal`
- Used by: HTTP requests after the security filter chain
- Pattern: Thin — no business logic

**Service:**
- Purpose: Business logic, transaction boundaries, external integration
- Examples: `backend/src/main/java/com/fintrack/auth/AuthService.java`, `backend/src/main/java/com/fintrack/portfolio/PortfolioService.java`, `backend/src/main/java/com/fintrack/price/PriceSyncService.java`, `backend/src/main/java/com/fintrack/budget/BudgetService.java`, `backend/src/main/java/com/fintrack/bills/BillService.java`, `backend/src/main/java/com/fintrack/notification/NotificationService.java`
- Depends on: Repositories, other services, external HTTP clients
- Used by: Controllers, schedulers, websocket broadcaster
- Pattern: `@Transactional` on writes, `@Transactional(readOnly=true)` on reads

**Repository:**
- Purpose: Persistence via Spring Data JPA
- Pattern: `interface XRepository extends JpaRepository<X, UUID>` with custom `@Query` where needed
- Examples: per-feature repositories (e.g. `backend/src/main/java/com/fintrack/portfolio/PortfolioRepository.java`)
- Used by: Services

**Domain / Entity:**
- Purpose: JPA-mapped domain models
- Where: `backend/src/main/java/com/fintrack/common/entity/`
- Examples: `User`, `RefreshToken`, `Asset`, `PriceHistory`, `Portfolio`, `PortfolioHolding`, `InvestmentTransaction`, `PortfolioSnapshot`, `BudgetTransaction`, `Bill`, `BillPayment`, `AlertNotification`, `AuditLog`
- Used by: Repositories and services

**DTO:**
- Purpose: Request and response contracts; no leakage of entities
- Where: `backend/src/main/java/com/fintrack/{feature}/dto/`
- Pattern: Java records with Jakarta Bean Validation annotations

**Frontend:**
- Pages (`frontend/src/pages/`) — route-level views, lazy-loaded from `frontend/src/App.tsx`
- Feature components (`frontend/src/components/{feature}/`) — UI building blocks
- Hooks (`frontend/src/hooks/`) — React Query wrappers + custom hooks (e.g. `useLivePrices`)
- API modules (`frontend/src/api/{feature}.api.ts`) — axios calls keyed off `frontend/src/api/client.ts`
- Stores (`frontend/src/store/`) — Zustand slices for `auth`, `prices`, theme, selected portfolio
- Types (`frontend/src/types/`) — TypeScript mirrors of backend DTOs

## Data Flow

**HTTP request lifecycle:**
1. Browser hits Nginx (TLS, rate limit, headers)
2. Nginx proxies `/api/*` to the Spring Boot container on port 8080
3. `RequestLoggingFilter` injects a request id into MDC and logs the request
4. `JwtAuthFilter` extracts the bearer token, validates it, and populates `SecurityContext`
5. Controller binds the request, runs `@Valid` validation, calls the service
6. Service runs inside `@Transactional`, talks to repositories, may hit Redis or external clients
7. Hibernate translates JPA calls into SQL against PostgreSQL
8. Service returns a record DTO; controller wraps it in `ResponseEntity` with the right status
9. `GlobalExceptionHandler` (`backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java`) maps any thrown exception to a uniform error response
10. Spring serializes JSON; Nginx returns it to the browser
11. Frontend axios client (`frontend/src/api/client.ts`) parses, surfaces errors to React Query, and on 401 attempts refresh via `/api/v1/auth/refresh` before retrying or redirecting to login

**Real-time price flow:**
1. Scheduler in `backend/src/main/java/com/fintrack/scheduler/` triggers `PriceSyncService` every 30 s (live tier) and hourly (funds)
2. `PriceSyncService` calls each external client (`CoinGeckoClient`, `ExchangeRateClient`, `PreciousMetalsClient`, `YahooFinanceClient`, `TefasClient`)
3. Updated assets and `PriceHistory` rows are persisted in a single transaction
4. `PriceBroadcaster` (`backend/src/main/java/com/fintrack/websocket/`) publishes the latest priced assets to `/topic/prices`
5. Frontend subscribes via `frontend/src/hooks/useLivePrices.ts`, updating the Zustand `prices` store
6. Components reading the store re-render with new values

**State management:**
- Server state: PostgreSQL is the source of truth; Redis caches transient data (price reads, session-side concerns, login rate counters)
- Client server-state: React Query with a 5 min default `staleTime`; mutations invalidate affected query keys
- Client UI state: Zustand stores (`auth.store.ts` persisted to localStorage; `prices.store.ts`, theme, selected portfolio kept in memory)

## Key Abstractions

| Abstraction | Purpose | Example file |
|---|---|---|
| Feature package | Vertical slice | `backend/src/main/java/com/fintrack/portfolio/` |
| Service | Business logic + transactions | `backend/src/main/java/com/fintrack/portfolio/PortfolioService.java` |
| Repository | Spring Data JPA CRUD | `backend/src/main/java/com/fintrack/portfolio/PortfolioRepository.java` |
| Entity | JPA domain model | `backend/src/main/java/com/fintrack/common/entity/Portfolio.java` |
| Record DTO | Immutable contract | `backend/src/main/java/com/fintrack/portfolio/dto/PortfolioResponse.java` |
| Scheduler | Time-driven jobs | `backend/src/main/java/com/fintrack/scheduler/` |
| External client | Third-party API adapter | `backend/src/main/java/com/fintrack/price/client/CoinGeckoClient.java` |
| Filter | Pre-controller hook | `backend/src/main/java/com/fintrack/auth/JwtAuthFilter.java` |
| Exception handler | Uniform error mapping | `backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java` |
| WebSocket broadcaster | Push fan-out | `backend/src/main/java/com/fintrack/websocket/` |
| React Query hook | Server-state binding | `frontend/src/hooks/usePortfolio.ts` |
| Zustand store | Client UI state | `frontend/src/store/auth.store.ts` |
| Axios client | HTTP boundary | `frontend/src/api/client.ts` |
| Flyway migration | Versioned schema change | `backend/src/main/resources/db/migration/V*.sql` |

## Entry Points

**Backend Spring main:**
- `backend/src/main/java/com/fintrack/FinTrackApplication.java`
- Annotated `@SpringBootApplication`; component-scans `com.fintrack.*`

**Frontend bootstrap:**
- `frontend/src/main.tsx` — sets up `QueryClientProvider`, router, and global providers
- `frontend/src/App.tsx` — route definitions including `ProtectedRoute`

**Schedulers:**
- `backend/src/main/java/com/fintrack/scheduler/` — price refresh, alerts, monthly reports

**WebSocket endpoint:**
- Configured under `backend/src/main/java/com/fintrack/websocket/`, exposed at `/ws`

## Error Handling

**Strategy:** Throw typed exceptions in services; one `@ControllerAdvice` (`GlobalExceptionHandler`) maps them to a uniform JSON shape with `error`, `code`, `requestId`, `timestamp`, `path`.

**Mapping:**
- `ResourceNotFoundException` → 404
- `BusinessRuleException` → 400 (with code)
- `MethodArgumentNotValidException` → 400 (with field details)
- `BadCredentialsException` → 401
- `LoginRateLimitException` → 429
- `AccessDeniedException` → 403
- Unhandled `Exception` → 500 (logged with stack)

**Frontend:** Axios interceptor handles 401 by calling refresh once; all other errors are surfaced to React Query, which feeds component error states.

## Cross-Cutting Concerns

**Authentication / Authorization:**
- Stateless Spring Security (`backend/src/main/java/com/fintrack/common/config/SecurityConfig.java`)
- Public paths: `/api/v1/auth/*`, `/api/v1/health/*`, `/ws/*`
- Everything else requires a valid JWT
- TOTP enforced for sensitive endpoints (`backend/src/main/java/com/fintrack/auth/TotpService.java`)

**Validation:** Jakarta Bean Validation on DTOs, surfaced via the global handler.

**Logging:** SLF4J + Logback. `RequestLoggingFilter` adds a request id to MDC; entries hit the configured log file plus console in dev.

**Audit:** `AuditLog` entity + `backend/src/main/java/com/fintrack/audit/AuditService.java`. Auth events covered today; domain mutations are a known gap.

**Caching:** Redis via Spring Cache abstractions for hot reads (price history, session helpers).

**Transactions:** `@Transactional` on service writes; default propagation `REQUIRED`; rollback on unchecked exceptions.

**Schema migrations:** Flyway-only; never edit a previously released `V{n}__*.sql`.

---

*Architecture analysis: 2026-05-03*
*Update when major patterns change*
