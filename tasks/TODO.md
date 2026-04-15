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
- [x] `PortfolioController.java` -- CRUD
- [x] `PortfolioService.java` -- list/get/create/update/delete with ownership
- [x] `AssetController.java` -- GET /api/v1/assets with type filter
- [x] `HoldingController.java` / `HoldingService.java` -- CRUD holdings under /api/v1/portfolios/{id}/holdings
- [x] Portfolio + holding DTOs (records)
- [x] SecurityConfig -- 401 entry point so frontend auto-refresh triggers
- [x] `SnapshotService.java` / `SnapshotScheduler.java` / `SnapshotController.java` -- daily snapshots + history endpoint
- [x] `PriceController.java` -- manual refresh endpoint
- [x] `PriceScheduler.java` -- 30-sec price sync with startup event
- [x] `CoinGeckoClient.java` -- BTC/ETH prices (batch /simple/price)
- [x] `TefasClient.java` -- Turkish fund prices (YAT + EMK via /api/DB/BindHistoryInfo) + V7 migration + hourly scheduler
- [x] `ExchangeRateClient.java` -- USD/TRY, EUR/TRY
- [x] `WebSocketConfig.java` -- STOMP /ws endpoint + /topic broker
- [x] `PriceBroadcaster.java` -- push prices to clients after each sync cycle
- [ ] Transactions module (deferred -- using direct holding edits for now)

### Frontend
- [x] `api/portfolio.api.ts` + `api/asset.api.ts` + `api/holding.api.ts`
- [x] `hooks/usePortfolios.ts` + `hooks/useAssets.ts` + `hooks/useHoldings.ts`
- [x] `PortfolioPage.tsx` -- list with create dialog and empty state
- [x] `PortfolioDetailPage.tsx` -- stats, holdings table, add/delete
- [x] `components/portfolio/PortfolioListItem.tsx` -- clickable link row
- [x] `components/portfolio/AddPortfolioDialog.tsx` -- 8 type options
- [x] `components/portfolio/HoldingsTable.tsx`
- [x] `components/portfolio/AddHoldingDialog.tsx` -- asset picker with search
- [x] `hooks/useRefreshPrices.ts` + `api/price.api.ts` -- manual refresh
- [x] React Query polling every 15s + flash animation on price change
- [x] WebSocket live prices -- STOMP /topic/prices broadcast from PriceBroadcaster + useLivePrices hook invalidates React Query on message
- [x] `components/portfolio/AllocationChart.tsx`
- [x] `components/portfolio/PortfolioHistoryChart.tsx` + `hooks/useSnapshots.ts` + `api/snapshot.api.ts`
- [x] Transaction log (controller, service, repo, DTOs; UI list + record dialog; auto-applies BUY/SELL to holding)

### Cross-cutting
- [x] i18n -- Turkish + English via react-i18next, LanguageSwitcher in AppShell + login, full coverage of nav/auth/portfolio/holdings/dashboard/budget/bills/settings

---

## Phase 3 -- Budget Module

### Backend
- [x] `BudgetController.java` -- transactions CRUD, summary, monthly snapshot
- [x] `BudgetService.java` -- transaction list/create/update/delete, summary computation, monthly snapshot capture
- [x] `CategoryController.java` + `CategoryService.java` -- income/expense CRUD
- [x] Repositories: TransactionRepository, IncomeCategoryRepository, ExpenseCategoryRepository, MonthlySummaryRepository
- [x] DTOs: CreateTransactionRequest, UpdateTransactionRequest, TransactionResponse, BudgetSummaryResponse, CategoryResponse, etc.

### Frontend
- [x] `hooks/useBudget.ts` -- useTransactions, useBudgetSummary, useCategories, useCreateTransaction, useDeleteTransaction, useCaptureSnapshot
- [x] `api/budget.api.ts` -- full API module
- [x] `BudgetPage.tsx` -- month navigator, KPI cards (income/expense/net/savings rate), transaction list with category dots, category breakdown bars
- [x] `components/budget/AddTransactionDialog.tsx` -- type toggle (income/expense), amount, category pill picker, description, date
- [x] `components/budget/MonthlyLogSection.tsx` -- historical log table with capture snapshot button

---

## Phase 4 -- Bills Module

### Backend
- [x] `BillController.java` -- CRUD, pay, history
- [x] `BillService.java` -- list with current period status, pay/skip, history
- [x] Repositories: BillRepository, BillPaymentRepository
- [x] DTOs: CreateBillRequest, PayBillRequest, BillResponse, PaymentHistoryResponse
- [x] `BillReminderScheduler.java` -- daily 08:00 check, logs reminder line per unpaid bill within remind window

### Frontend
- [x] `hooks/useBills.ts` -- useBills, useCreateBill, useDeleteBill, usePayBill
- [x] `api/bills.api.ts` -- full API module
- [x] `BillsPage.tsx` -- KPI strip (due/paid/pending), bill list with status dots, pay button, delete
- [x] `components/bills/AddBillDialog.tsx` -- name, amount, due day, category
- [x] `components/bills/BillsCalendar.tsx` -- month grid with due-day markers (paid/pending/urgent colors)

---

## Phase 5 -- Dashboard + Polish

- [x] `GET /api/v1/dashboard` -- DashboardController + DashboardService (aggregates portfolios, budget, upcoming bills)
- [x] `DashboardPage.tsx` -- live KPI cards (net worth, income, expense, savings rate), portfolio cards with P&L, upcoming bills list
- [x] `api/dashboard.api.ts` + `hooks/useDashboard.ts`
- [x] `AppShell.tsx` -- sidebar + topbar + mobile nav (completed in Phase 1/2)
- [x] `components/dashboard/LivePriceTicker.tsx` (STOMP-driven, shown on DashboardPage)
- [x] `SettingsPage.tsx` -- language selector (completed in Phase 2)
- [x] `AnalyticsPage.tsx` (savings rate area, income/expense bars, aggregated portfolio value line, CAGR + growth KPIs)
- [x] Dark/light/system mode toggle wired to user_settings via /api/v1/settings
- [x] Mobile responsive layout (sidebar becomes off-canvas drawer below md, hamburger in topbar, backdrop overlay, page padding responsive)

---

## Phase 6 -- Reports & Export

- [x] `ReportController.java` -- `/api/v1/reports/portfolio/{id}` + `/api/v1/reports/budget`
- [x] `ReportService.java` -- OpenPDF-based PDF generation + CSV streaming
- [x] PDF portfolio report (title, summary KPIs, holdings table with P&L)
- [x] CSV transaction export (date range, includes categories + tags)
- [x] Frontend download buttons -- `api/report.api.ts`, Download icon button in PortfolioDetailPage and BudgetPage

---

## Phase 7 -- AI Analysis (optional)

Skipped: no Anthropic API key available for this deployment.

- [ ] `AiAnalysisController.java` (skipped)
- [ ] `ClaudeApiClient.java` (skipped)
- [ ] Frontend AI analysis panel (skipped)

---

## Localization Hardening

- [x] Locale-aware number formatting (formatters.ts reads i18n.resolvedLanguage)
- [x] Locale-aware date formatting across all views (formatters.ts reads i18n.resolvedLanguage)
- [x] Currency selection in user_settings (settings API + store-driven formatCurrency, TRY/USD/EUR/GBP selector)
- [ ] Audit all user-facing strings for missing i18n keys (error messages, toasts, empty states)
- [x] Backend MessageSource + AcceptHeaderLocaleResolver + validation bundles (en/tr)
- [x] Timezone stored in user_settings; formatters pass `timeZone` to Intl.DateTimeFormat

---

---

## Deployment

- [x] `nginx/nginx.conf` -- HTTP to HTTPS redirect + TLS server for fatihaciroglu.dev + locations/security snippets
- [x] `docker-compose.yml` -- certbot service with webroot auto-renew loop
- [x] `scripts/ssl-setup.sh` -- webroot-based issuance for fatihaciroglu.dev (STAGING mode supported)
- [x] `scripts/smoke-test.sh` -- automated end-to-end API check
- [x] `scripts/restore.sh` -- pg_restore counterpart to backup.sh
- [x] `scripts/fintrack-backup.service` + `.timer` -- daily backups via systemd
- [x] `docs/DEPLOYMENT.md` -- self-hosted install + SSL + auto-start guide
- [x] `docs/SMOKE_TEST.md` -- automated + manual UI checklist

---

## Bugs

- [x] Login: wrong password shows error then page auto-refreshes and clears the message; keep the error visible and prevent the reload

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
