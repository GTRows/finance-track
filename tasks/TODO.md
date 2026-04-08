# TODO -- Implementation Plan

Update this file as you complete tasks. Mark done with [x].

## Phase 1 -- Foundation (start here)

### Infrastructure
- [x] `docker-compose.yml` -- all 5 services wired up + app-logs volume
- [x] `nginx/nginx.conf` -- routing rules (HTTP only for local dev, /api/v1/ prefix)
- [x] `.env.example` -- all variables documented
- [x] `scripts/fintrack.service` -- systemd auto-start
- [x] `scripts/backup.sh` -- pg_dump daily backup
- [x] `scripts/ssl-setup.sh` -- Let's Encrypt setup
- [x] `scripts/setup.sh` -- first-time setup helper
- [x] `backend/Dockerfile` -- multi-stage Maven build
- [x] `frontend/Dockerfile` -- multi-stage Node build
- [x] `.gitignore` -- comprehensive ignore rules

### Backend -- Core Setup
- [x] `pom.xml` -- all dependencies
- [x] `application.yml` -- config with env var substitution + logging config
- [x] `FinTrackApplication.java` -- main class
- [x] `db/migration/V1__initial_schema.sql` -- all tables (schema only)
- [x] `db/migration/V2__seed_assets.sql` -- BTC, ETH, TTA, ITP, TIE, TMG, TI1, ABE, AH5, BHT, BGL, AH3
- [x] `db/migration/V3__seed_categories.sql` -- placeholder (categories created per-user)
- [x] `db/migration/V4__admin_settings.sql` -- admin configuration table
- [x] All JPA entities (User, Asset, Portfolio, PortfolioHolding, InvestmentTransaction, etc.)
- [x] Global exception handler (`@ControllerAdvice`) with consistent error format + requestId
- [x] Health endpoint (`GET /api/v1/health`) -- enhanced with component status
- [x] `logback-spring.xml` -- structured logging (JSON prod, readable dev, rolling files)
- [x] `RequestLoggingFilter` -- MDC requestId, method/path/status/duration logging
- [x] Admin controller -- log management, settings, system info endpoints

### Backend -- Auth Module
- [x] `JwtUtil.java` -- generate + validate tokens
- [x] `JwtAuthFilter.java` -- request filter
- [x] `SecurityConfig.java` -- Spring Security setup (/api/v1/ prefix, ADMIN role)
- [x] `FinTrackUserDetailsService.java`
- [x] `FinTrackUserDetails.java` -- UserDetails implementation
- [x] `AuthController.java` -- register, login, refresh, logout, /me
- [x] `AuthService.java` -- business logic
- [x] `RefreshTokenService.java` -- DB-backed token management
- [x] Auth DTOs (records)

### Frontend -- Core Setup
- [x] `package.json` -- all dependencies
- [x] `vite.config.ts` -- API proxy config
- [x] `tailwind.config.ts` -- custom colors (dark mode default)
- [x] shadcn/ui installation and configuration (Button, Input, Card, Label)
- [x] `src/api/client.ts` -- axios with JWT interceptor + auto-refresh
- [x] `src/store/auth.store.ts` -- Zustand auth state
- [x] `src/types/` -- all TypeScript interfaces
- [x] `src/utils/formatters.ts` -- TRY, %, date formatting (Turkish locale)
- [x] `src/utils/calculations.ts` -- P&L, allocation weight, savings rate

### Frontend -- Auth
- [x] `LoginPage.tsx` -- login + register form (toggle)
- [x] Route protection (redirect if not authed)
- [x] Token refresh logic in axios interceptor
- [x] `DashboardPage.tsx` -- placeholder dashboard after login
- [x] `AppShell.tsx` -- header with user info and logout

---

## Phase 2 -- Portfolio Module

### Backend
- [ ] `PortfolioController.java` -- CRUD + transactions
- [ ] `PortfolioService.java` -- holding calculations
- [ ] `HoldingService.java` -- update on transaction
- [ ] `SnapshotService.java` -- daily snapshots
- [ ] `PriceController.java` -- current prices endpoint
- [ ] `PriceSyncScheduler.java` -- 5-min price sync
- [ ] `CoinGeckoClient.java` -- BTC/ETH prices
- [ ] `TefasClient.java` -- Turkish fund prices
- [ ] `ExchangeRateClient.java` -- USD/TRY, EUR/TRY
- [ ] `WebSocketConfig.java` -- STOMP setup
- [ ] `PriceBroadcaster.java` -- push prices to clients
- [ ] Portfolio DTOs

### Frontend
- [ ] `store/prices.store.ts` -- live price state
- [ ] `hooks/useLivePrices.ts` -- WebSocket subscription
- [ ] `hooks/usePortfolio.ts` -- React Query hooks
- [ ] `api/portfolio.api.ts` -- API calls
- [ ] `PortfolioPage.tsx` -- full portfolio view
- [ ] `components/portfolio/HoldingsTable.tsx`
- [ ] `components/portfolio/AllocationChart.tsx`
- [ ] `components/portfolio/PortfolioHistoryChart.tsx`
- [ ] `components/portfolio/AddTransactionDialog.tsx`
- [ ] `components/portfolio/TransactionLog.tsx`

---

## Phase 3 -- Budget Module

### Backend
- [ ] `TransactionController.java`
- [ ] `TransactionService.java`
- [ ] `BudgetSummaryService.java`
- [ ] `CategoryService.java`
- [ ] Budget DTOs

### Frontend
- [ ] `hooks/useBudget.ts`
- [ ] `api/budget.api.ts`
- [ ] `BudgetPage.tsx`
- [ ] `components/budget/BudgetSummaryBar.tsx`
- [ ] `components/budget/CategoryBreakdown.tsx`
- [ ] `components/budget/TransactionList.tsx`
- [ ] `components/budget/AddTransactionDialog.tsx`
- [ ] `components/budget/MonthlyLogSection.tsx` -- historical log table
- [ ] `components/budget/SnapshotButton.tsx` -- capture month-end

---

## Phase 4 -- Bills Module

### Backend
- [ ] `BillController.java`
- [ ] `BillService.java`
- [ ] `BillReminderScheduler.java` -- morning check, send notifications

### Frontend
- [ ] `hooks/useBills.ts`
- [ ] `api/bills.api.ts`
- [ ] `BillsPage.tsx`
- [ ] `components/bills/BillsList.tsx`
- [ ] `components/bills/BillCard.tsx`
- [ ] `components/bills/BillsCalendar.tsx`
- [ ] `components/bills/AddBillDialog.tsx`

---

## Phase 5 -- Dashboard + Polish

- [ ] `GET /api/v1/dashboard` endpoint
- [ ] `DashboardPage.tsx` -- all widgets
- [ ] `components/dashboard/KpiCards.tsx`
- [ ] `components/dashboard/UpcomingBillsWidget.tsx`
- [ ] `components/dashboard/RecentTransactionsList.tsx`
- [ ] `AppShell.tsx` -- sidebar + topbar + mobile nav
- [ ] `components/layout/LivePriceTicker.tsx`
- [ ] `SettingsPage.tsx`
- [ ] `AnalyticsPage.tsx`
- [ ] Dark/light mode toggle
- [ ] Mobile responsive layout

---

## Phase 6 -- Reports & Export

- [ ] `ReportController.java`
- [ ] `ReportService.java` -- PDF generation
- [ ] PDF portfolio report
- [ ] CSV transaction export
- [ ] Frontend download buttons

---

## Phase 7 -- AI Analysis (optional)

- [ ] `AiAnalysisController.java`
- [ ] `ClaudeApiClient.java`
- [ ] Frontend AI analysis panel

---

## Future Ideas (backlog)

- [ ] Import from existing Excel (`Yatirim_Takip_final_v2.xlsx`)
- [ ] 2FA / TOTP
- [ ] Budget rules engine (alerts when over budget)
- [ ] Price alerts (notify when BTC hits X)
- [ ] React Native mobile app
- [ ] TimescaleDB for price history (if performance needed)
- [ ] GitHub Actions CI/CD
- [ ] Grafana monitoring
- [ ] Multi-user / family sharing
