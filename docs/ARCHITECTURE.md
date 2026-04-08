# Architecture

## System Overview

FinTrack Pro is a single-user (owner) self-hosted web application.
It runs entirely in Docker on a local machine or VPS. No external cloud services required.

```
[Browser / Mobile]
       ↕ HTTPS (port 443) or HTTP (port 80 for local dev)
[Nginx] — reverse proxy, SSL termination, rate limiting
       ↕ Internal Docker network (fintrack-net)
   ┌──────────────┬──────────────┐
[Frontend]    [Backend API]   [WebSocket]
React SPA     Spring Boot     STOMP/WS
:3000          :8080           :8080/ws
                  ↕
          ┌───────┴────────┐
      [PostgreSQL]      [Redis]
         :5432            :6379
                  ↕
          [External APIs]
    CoinGecko, TEFAS, ExchangeRate
```

## Docker Services

Five containers in `fintrack-net` bridge network:

| Container | Image | Port | Purpose |
|-----------|-------|------|---------|
| `fintrack-nginx` | nginx:1.25-alpine | 80, 443 | Reverse proxy + SSL |
| `fintrack-ui` | custom build | 3000 (internal) | React SPA |
| `fintrack-api` | custom build | 8080 (internal) | Spring Boot API |
| `fintrack-postgres` | postgres:16-alpine | 5432 (internal) | Primary DB |
| `fintrack-redis` | redis:7-alpine | 6379 (internal) | Cache + sessions |

All containers use `restart: always` — auto-start on system boot.
Data persists via named Docker volumes (`postgres-data`, `redis-data`).

## Nginx Routing

```
GET  /api/*     → backend:8080/api/*      (rate: 30 req/min)
POST /api/auth/* → backend:8080/api/auth/*  (rate: 5 req/min — brute force protection)
GET  /ws/*      → backend:8080/ws/*       (WebSocket upgrade)
GET  /*         → frontend:3000/*         (React SPA)
```

All routes through single Nginx entry point. Frontend and backend never exposed directly.

## Backend Architecture

**Pattern:** Feature-based package structure. Each feature is a vertical slice.

```
com.fintrack/
├── auth/           # Authentication & authorization
├── portfolio/      # Investment portfolio management
├── budget/         # Income & expense tracking
├── bills/          # Recurring bill management
├── price/          # Live price fetching & sync
├── notification/   # Email + in-app notifications
├── report/         # PDF/CSV generation
├── ai/             # Claude AI analysis (optional)
├── websocket/      # STOMP WebSocket configuration
└── common/
    ├── entity/     # JPA entities (shared across features)
    ├── dto/        # Shared DTOs
    ├── exception/  # Global exception handler
    └── config/     # Security, Redis, CORS, WebSocket config
```

**Within each feature package:**
```
portfolio/
├── PortfolioController.java    # REST endpoints (thin — no business logic)
├── PortfolioService.java       # Business logic (@Transactional)
├── HoldingService.java         # Holding-specific logic
├── SnapshotService.java        # Daily snapshot logic
├── dto/
│   ├── PortfolioResponse.java  # record — what we return
│   ├── CreatePortfolioRequest.java  # record — what we accept
│   └── HoldingResponse.java
└── (entities in common/entity/)
```

**Request lifecycle:**
```
HTTP Request
  → Nginx (rate limit, header injection)
  → JwtAuthFilter (validate token, set SecurityContext)
  → Controller (validate request DTO with @Valid)
  → Service (business logic, DB access via Repository)
  → Repository (Spring Data JPA)
  → PostgreSQL
  → Response DTO
  → ResponseEntity<T>
  → HTTP Response
```

## Frontend Architecture

**Pattern:** Feature-based component organization + React Query for server state.

```
src/
├── pages/           # One file per route — thin, import from features
├── components/
│   ├── layout/      # AppShell, Sidebar, Topbar, MobileNav
│   ├── dashboard/   # KPI cards, portfolio summary, recent transactions
│   ├── portfolio/   # Holdings table, allocation chart, transaction log
│   ├── budget/      # Income/expense forms, category breakdown, monthly log
│   ├── bills/       # Bill list, calendar view, payment form
│   ├── charts/      # Recharts wrappers (PortfolioChart, BudgetChart, etc.)
│   └── ui/          # shadcn/ui re-exports and customizations
├── api/
│   ├── client.ts    # Axios instance with JWT interceptor + refresh logic
│   ├── auth.api.ts
│   ├── portfolio.api.ts
│   ├── budget.api.ts
│   ├── bills.api.ts
│   └── prices.api.ts
├── store/
│   ├── auth.store.ts      # Zustand: current user, tokens
│   ├── portfolio.store.ts # Zustand: selected portfolio, filters
│   └── prices.store.ts    # Zustand: live price map (updated via WS)
├── hooks/
│   ├── usePortfolio.ts    # React Query wrapper for portfolio API
│   ├── useBudget.ts       # React Query wrapper for budget API
│   ├── useBills.ts        # React Query wrapper for bills API
│   └── useLivePrices.ts   # WebSocket hook — subscribes to /topic/prices
├── types/                 # TypeScript interfaces (match backend DTOs exactly)
└── utils/
    ├── formatters.ts      # formatTRY(), formatPercent(), formatDate()
    └── calculations.ts    # P&L calc, allocation weight, savings rate
```

**State management split:**
- **React Query:** all server data (portfolios, transactions, bills, prices history)
- **Zustand:** client UI state (auth session, live prices from WebSocket, UI preferences)
- **No prop drilling:** hooks are called at page or feature-component level

## Real-Time Architecture

Live price updates via WebSocket:

```
PriceSyncScheduler (every 5 min)
  → fetches prices from CoinGecko + TEFAS + ExchangeRate
  → saves to PostgreSQL (price_history table)
  → updates Redis cache (key: "price:{symbol}", TTL: 5min)
  → broadcasts to WebSocket topic: /topic/prices

Frontend (useLivePrices hook)
  → STOMP client connects to ws://api/ws
  → subscribes to /topic/prices
  → updates prices.store (Zustand)
  → components re-render reactively
```

## Authentication Flow

See `docs/SECURITY.md` for full details. Summary:

1. `POST /api/auth/login` → returns `{accessToken, refreshToken}`
2. Frontend stores accessToken in memory (Zustand), refreshToken in httpOnly cookie
3. Every API request: `Authorization: Bearer <accessToken>`
4. On 401: frontend auto-calls `POST /api/auth/refresh` → gets new token pair
5. Logout: `POST /api/auth/logout` → server deletes refresh token from DB

## Deployment Targets

| Target | Config | Notes |
|--------|--------|-------|
| Windows + Docker Desktop | `docker-compose.yml` + HTTP only | Local dev & initial use |
| Linux VPS | `docker-compose.yml` + `docker-compose.prod.yml` | Add SSL, domain |
| AWS | ECS or EC2 + docker compose | Same containers, add ALB for HTTPS |

Migration path: Local → VPS → AWS requires only environment variable changes.
The application code doesn't change between environments.

## Scalability Notes

Current design is single-instance (one of each container).
If load ever requires scaling:
- Backend: stateless (JWT), can run multiple instances behind Nginx load balancer
- Frontend: static files, trivially scalable
- PostgreSQL: connection pooling via HikariCP (already configured)
- Redis: session/cache shared across instances (already designed this way)
- WebSocket: would need Redis pub/sub for multi-instance (future concern)
