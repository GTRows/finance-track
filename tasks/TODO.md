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
- [x] Transactions module (`InvestmentTransactionController/Service/Repository` + DTOs; BUY/SELL auto-applies to the related holding)

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

## Phase 7 -- AI Analysis

Cancelled. No Anthropic API key is provisioned for this deployment and the feature
is not going to be built. The `ai/` backend package, `CLAUDE_API_KEY` / `CLAUDE_ENABLED`
env vars, and related frontend panels are intentionally absent.

---

## Localization Hardening

- [x] Locale-aware number formatting (formatters.ts reads i18n.resolvedLanguage)
- [x] Locale-aware date formatting across all views (formatters.ts reads i18n.resolvedLanguage)
- [x] Currency selection in user_settings (settings API + store-driven formatCurrency, TRY/USD/EUR/GBP selector)
- [x] Audit all user-facing strings for missing i18n keys (error messages, toasts, empty states)
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

- [x] Import from existing Excel (`Yatirim_Takip_final_v2.xlsx`)
- [x] 2FA / TOTP (RFC 6238 via authenticator apps, challenge-token login flow)
- [x] Budget rules engine (alerts when over budget)
- [x] Price alerts (notify when BTC hits X)
- [x] GitHub Actions CI (backend mvn test + frontend typecheck/build + Docker build)
- [x] Grafana monitoring (Prometheus scraping + provisioned overview dashboard)

### Deferred (intentionally out of scope)

- [ ] React Native mobile app -- separate codebase; current PWA + responsive layout covers mobile usage
- [ ] TimescaleDB for price history -- premature optimization; snapshots on plain PostgreSQL are fine at single-user scale
- [ ] Multi-user / family sharing -- conflicts with single-owner scope defined in `CLAUDE.md`; would require re-auditing every ownership check

---

## Phase 8 -- Account & Auth Hardening

Goal: finish the account lifecycle and get email flows live. Prerequisite for any
email-reminder work in later phases.

- [x] 8.1  SMTP integration -- wire JavaMailSender, template engine, test endpoint
- [x] 8.2  Password change flow -- `POST /auth/password` + settings form (replace the disabled button)
- [x] 8.3  Active sessions view -- list refresh tokens, revoke one or all-but-current
- [x] 8.4  Login rate limit -- Redis token-bucket, N failures per IP and per username
- [x] 8.5  Email verification on register -- `email_verifications` table, confirm link, resend
- [x] 8.6  Password reset via email -- request + confirm endpoints, single-use token, short TTL

## Phase 9 -- Portfolio Expansion

- [x] 9.1  Watchlist (assets tracked without owning)
- [x] 9.2  Target allocation + rebalance drift view
- [x] 9.3  Risk metrics -- Sharpe, volatility, max drawdown (computed from snapshots)
- [x] 9.4  Precious metals spot via gold-api.com (keyless USD/oz) × live USD/TRY FX rate
- [ ] 9.5  BIST equities via Alpha Vantage or Finnhub free tier (e.g. `THYAO.IS`)

## Phase 10 -- Budget & Expenses Deepening

- [x] 10.1 Recurring transaction templates (salary, rent, subscriptions outside Bills)
- [ ] 10.2 Multi-currency transactions (foreign expense auto-converted to preferred currency)
- [x] 10.3 Category rollover (unused monthly budget carried forward)
- [ ] 10.4 Receipt photo upload (filesystem storage, OCR marked as follow-up)
- [x] 10.5 Transaction tags (many-to-many `transaction_tags` table)
- [x] 10.6 Rule-based auto-categorization (merchant -> category learned from history)
- [ ] 10.7 Cash flow allocator -- new feature. Inputs: income, obligatory outflows
        (credit card minimums, HOA dues, fixed debts) and optional buckets
        (savings, investments, cash buffer). Output: suggested distribution with
        a default based on user's settings, editable step-by-step before committing.

## Phase 11 -- Bills Polish

- [x] 11.1 Email reminders (uses Phase 8 SMTP)
- [ ] 11.2 Push reminders (uses Phase 16 Web Push)
- [x] 11.3 Bill amount variance tracking (month-over-month delta flag)
- [x] 11.4 Subscription audit (unused-in-N-months detector, manual mark-as-used)

## Phase 12 -- Dashboard & Analytics

- [x] 12.1 Net worth history with annotations (big purchases, income events)
- [x] 12.2 Savings goal tracker (target, current, projected completion)
- [x] 12.3 Debt tracker with amortization projection
- [x] 12.4 FIRE calculator (savings rate, withdrawal rate, time-to-independence)
- [x] 12.5 Custom date range picker across analytics views (presets + custom range, filters budget + portfolio series)

## Phase 13 -- Reporting & Portability

- [x] 13.1 Monthly PDF email report (scheduler + email attachment)
- [x] 13.2 xlsx export (in addition to existing CSV)
- [x] 13.3 Full JSON backup + restore (user-initiated download and upload)

## Phase 14 -- UX

- [x] 14.1 Global command palette (Cmd/Ctrl+K over nav, portfolios, bills; topbar trigger + recent items)
- [x] 14.2 Bulk operations (multi-select delete / edit / tag)
- [x] 14.3 Pinned holdings (favourites at top of list)
- [ ] 14.4 First-run onboarding wizard
- [x] 14.5 PWA manifest + installable + offline shell

## Phase 15 -- Infrastructure Integration

The target deployment is a unified homelab: Portainer + Traefik + Authelia +
WireGuard + CrowdSec + ModSecurity + Trivy + Wazuh + Prometheus/Grafana/Loki +
Alertmanager + Homarr + Restic + Syncthing. FinTrack integrates as one of the
applications behind this stack.

- [x] 15.1 Restic-based encrypted backups replacing raw `pg_dump` loop (S3/B2 target configurable)
- [x] 15.2 Trivy image + filesystem scan step in GitHub Actions
- [x] 15.3 Grafana business dashboard (portfolio total value, tx/day, savings rate, alerts fired)
- [x] 15.4 Dependabot auto-merge on green CI
- [x] 15.5 PostgreSQL index audit (Flyway migration for missing indexes)
- [x] 15.6 Traefik labels on every service; drop bundled Nginx to a fallback `nginx` profile
- [x] 15.7 Authelia ForwardAuth pass-through -- trust `Remote-User` header when enabled
- [x] 15.8 CrowdSec feed -- expose auth-failure and TOTP-failure events via audit_log format
        consumable by CrowdSec scenarios
- [x] 15.9 Loki log shipping via Promtail sidecar (structured JSON already emitted)
- [x] 15.10 Wazuh agent compatibility -- document log paths and action field set
- [x] 15.11 Homarr tile snippet (icon, URL, health endpoint) in `docs/`

## Phase 16 -- Notifications

- [ ] 16.1 Web Push API (VAPID keys, service worker subscription, notification payload)
