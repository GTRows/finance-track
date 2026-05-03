# Codebase Structure

**Analysis Date:** 2026-05-03

## Directory Layout

```
fintrack/
├── backend/                              # Spring Boot 3.2 / Java 21
│   ├── src/main/java/com/fintrack/
│   │   ├── auth/                         # JWT, login, register, refresh, 2FA (TOTP)
│   │   ├── portfolio/                    # Holdings, transactions, snapshots
│   │   ├── budget/                       # Income, expenses, monthly summaries, rules
│   │   ├── bills/                        # Recurring bills, payment history
│   │   ├── price/                        # Price sync orchestrator + external clients
│   │   │   └── client/                   # CoinGecko / TEFAS / ExchangeRate / Yahoo / Metals
│   │   ├── notification/                 # Email + in-app alerts
│   │   ├── report/                       # PDF / CSV export
│   │   ├── ai/                           # Optional Claude integration
│   │   ├── websocket/                    # STOMP config + broadcasters
│   │   ├── scheduler/                    # @Scheduled jobs
│   │   ├── analytics/                    # Aggregations / trends
│   │   ├── alert/                        # Price alerts
│   │   ├── audit/                        # Audit log writer
│   │   ├── admin/                        # Admin settings
│   │   ├── asset/                        # Asset master list / seeding
│   │   ├── dashboard/                    # Dashboard aggregator
│   │   ├── debt/                         # Debt tracking
│   │   ├── fire/                         # FIRE calculations
│   │   ├── health/                       # Health endpoints
│   │   ├── imports/                      # Excel/CSV import service
│   │   ├── metrics/                      # Custom metrics
│   │   ├── networth/                     # Net worth tracking
│   │   ├── push/                         # Web push (VAPID)
│   │   ├── savings/                      # Savings goals
│   │   ├── settings/                     # User preferences
│   │   ├── tag/                          # Transaction tags
│   │   ├── watchlist/                    # Asset watchlist
│   │   ├── backup/                       # Per-user export / import
│   │   ├── common/
│   │   │   ├── entity/                   # JPA entities
│   │   │   ├── config/                   # Security, Redis, CORS, WebSocket, i18n
│   │   │   ├── exception/                # GlobalExceptionHandler + custom exceptions
│   │   │   ├── filter/                   # JwtAuthFilter, RequestLoggingFilter
│   │   │   └── web/                      # Shared web records (ErrorResponse, etc.)
│   │   └── FinTrackApplication.java      # Spring entry point
│   ├── src/main/resources/
│   │   ├── application.yml               # Profile-driven config
│   │   ├── db/migration/V*.sql           # Flyway migrations
│   │   └── i18n/                         # Locale message bundles
│   ├── src/test/java/com/fintrack/       # JUnit 5 + Mockito + WebMvc tests
│   ├── pom.xml                           # Maven manifest
│   └── Dockerfile                        # Multi-stage build → JRE 21 image
│
├── frontend/                             # React 18 + Vite + TypeScript
│   ├── src/
│   │   ├── pages/                        # Route-level views
│   │   ├── components/
│   │   │   ├── layout/                   # AppShell, ProtectedRoute, Topbar, Sidebar
│   │   │   ├── dashboard/                # KPI cards, summary widgets
│   │   │   ├── portfolio/                # Holdings table, allocation chart, tx log
│   │   │   ├── budget/                   # Income/expense forms
│   │   │   ├── bills/                    # Bill list, calendar, payment form
│   │   │   ├── analytics/                # Net worth, savings trend, performance
│   │   │   ├── alerts/                   # Price alert UI
│   │   │   ├── prices/                   # Live ticker, asset search
│   │   │   ├── settings/                 # Profile, categories, password
│   │   │   ├── charts/                   # Recharts wrappers
│   │   │   └── ui/                       # shadcn/ui primitives
│   │   ├── api/
│   │   │   ├── client.ts                 # Axios + interceptors
│   │   │   └── *.api.ts                  # Per-feature API modules
│   │   ├── hooks/                        # React Query + custom (useLivePrices, …)
│   │   ├── store/                        # Zustand slices (auth, prices, theme)
│   │   ├── types/                        # TS interfaces mirroring backend DTOs
│   │   ├── utils/                        # formatters, calculators, validators
│   │   ├── lib/                          # Third-party client wrappers (stomp)
│   │   ├── i18n/                         # Locale config + translations
│   │   ├── test-utils/                   # Test wrappers (Query/Provider)
│   │   ├── App.tsx                       # Routes
│   │   ├── main.tsx                      # React entry point
│   │   └── index.css                     # Tailwind + globals
│   ├── public/                           # index.html, manifest.json, sw.js
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── .eslintrc.cjs
│   └── Dockerfile                        # Multi-stage → nginx image
│
├── nginx/                                # Reverse proxy config (SSL, rate limit)
├── monitoring/                           # Prometheus, Grafana, Promtail
├── scripts/                              # setup.sh, backup.sh, ssl-setup.sh, fintrack.service
├── docs/                                 # ARCHITECTURE, DATABASE, API, FRONTEND, …
├── tasks/                                # TODO.md, LESSONS.md, ROADMAP.md
├── .planning/                            # Planning artifacts (this directory)
├── .claude/                              # Claude Code project config
├── .github/workflows/                    # CI pipelines
├── docker-compose.yml                    # Production stack
├── docker-compose.traefik.yml            # Traefik variant
├── .env.example                          # Env template
├── CLAUDE.md                             # Project brain
├── README.md
└── RELEASE.md
```

## Directory Purposes

- `backend/` — Spring Boot service: REST + WebSocket + scheduled jobs + Flyway migrations
- `frontend/` — React SPA: pages, feature components, hooks, stores, API clients
- `nginx/` — Reverse proxy: TLS, rate limiting, header injection, static asset serving
- `monitoring/` — Prometheus scrape config, Grafana dashboards/provisioning, optional Promtail
- `scripts/` — Operational shell scripts and the systemd unit
- `docs/` — Authoritative specs (read before editing the corresponding module)
- `tasks/` — Lightweight project tracking (kept in repo, not a tracker)
- `.planning/` — Planning state used by the GSD workflow (PROJECT, ROADMAP, phases, codebase map)
- `.claude/` — Project-scoped Claude Code settings, hooks, and skills
- `.github/` — CI workflows and issue templates

## Key File Locations

**Entry points:**
- `backend/src/main/java/com/fintrack/FinTrackApplication.java`
- `frontend/src/main.tsx`, `frontend/src/App.tsx`

**Configuration:**
- Backend: `backend/src/main/resources/application.yml`, `backend/src/main/java/com/fintrack/common/config/SecurityConfig.java`
- Frontend: `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/tailwind.config.ts`, `frontend/.eslintrc.cjs`
- Stack: `docker-compose.yml`, `nginx/nginx.conf`, `.env.example`

**Core logic:**
- Auth: `backend/src/main/java/com/fintrack/auth/`
- Portfolio: `backend/src/main/java/com/fintrack/portfolio/`
- Budget: `backend/src/main/java/com/fintrack/budget/`
- Bills: `backend/src/main/java/com/fintrack/bills/`
- Prices: `backend/src/main/java/com/fintrack/price/`
- Frontend HTTP boundary: `frontend/src/api/client.ts`

**Testing:**
- Backend tests: `backend/src/test/java/com/fintrack/`
- Frontend tests: collocated `*.test.ts` / `*.test.tsx`
- Frontend test utilities: `frontend/src/test-utils/`

**Documentation:**
- `CLAUDE.md` (project brain), `docs/*.md`, `tasks/TODO.md`, `tasks/LESSONS.md`

## Naming Conventions

**Java / backend:**
- Packages: lowercase, feature-named — `com.fintrack.portfolio`
- Classes: PascalCase — `PortfolioController`, `PriceSyncService`, `JwtAuthFilter`
- Methods: camelCase, verb-first
- Constants: SCREAMING_SNAKE_CASE
- Records (DTOs): suffix `Request` or `Response` — `CreatePortfolioRequest`, `PortfolioResponse`
- Repositories: `XRepository`; Services: `XService`; Controllers: `XController`
- Tests: `XTest` (unit), `XWebMvcTest` (controller slice)

**TypeScript / frontend:**
- Component files: PascalCase `.tsx` — `PortfolioCard.tsx`
- Hooks: camelCase with `use` prefix — `usePortfolio.ts`
- API modules: `{feature}.api.ts`
- Stores: `{feature}.store.ts`
- Types: `{feature}.types.ts`
- Path alias: `@/*` → `./src/*`

**Database:**
- Tables and columns: `snake_case`
- Primary key: `id UUID`
- Foreign keys: `{entity}_id`
- Migrations: `V{n}__{description}.sql`, never edit a published file

## Where to Add New Code

**New backend feature:**
1. New package `backend/src/main/java/com/fintrack/{feature}/`
2. `XController.java`, `XService.java`, `XRepository.java`, `dto/XRequest.java`, `dto/XResponse.java`
3. Add entity to `common/entity/` if new tables are introduced
4. Add migration `backend/src/main/resources/db/migration/V{next}__{description}.sql`
5. Add tests under `backend/src/test/java/com/fintrack/{feature}/`

**New frontend feature:**
1. Page in `frontend/src/pages/`, route added to `frontend/src/App.tsx`
2. Components in `frontend/src/components/{feature}/`
3. Hook in `frontend/src/hooks/use{Feature}.ts`
4. API module in `frontend/src/api/{feature}.api.ts`
5. Types in `frontend/src/types/{feature}.types.ts`
6. Store in `frontend/src/store/{feature}.store.ts` only if shared across many components
7. Tests collocated as `*.test.ts(x)`

**New migration:** copy the latest `V{n}` filename, increment, never edit existing files.

**New ADR:** see `gtr:new-adr` skill; lands under `docs/adr/`.

## Special Directories

- `backend/src/main/resources/db/migration/` — Flyway, append-only
- `backend/src/main/resources/i18n/` — message bundles
- `frontend/src/components/ui/` — shadcn/ui re-exports; do not reinvent primitives
- `monitoring/grafana/dashboards/` — committed dashboard JSON
- `.planning/codebase/` — output of this map; refresh with `/gsd:map-codebase`
- `.env` — never committed; produced from `.env.example`

---

*Structure analysis: 2026-05-03*
*Update when directory structure changes*
