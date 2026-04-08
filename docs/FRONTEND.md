# Frontend Architecture

## Stack

| Tool | Version | Purpose |
|------|---------|---------|
| React | 18 | UI framework |
| TypeScript | 5 | Type safety (strict mode) |
| Vite | 5 | Build tool + dev server |
| Tailwind CSS | 3 | Utility-first styling |
| shadcn/ui | latest | Accessible component primitives |
| Recharts | 2 | Charts (portfolio, budget) |
| Zustand | 4 | Client state (auth, live prices) |
| React Query (TanStack) | 5 | Server state (API data) |
| React Router | 6 | Routing |
| Axios | 1 | HTTP client |
| @stomp/stompjs | 6 | WebSocket client (live prices) |
| date-fns | 3 | Date formatting |
| lucide-react | latest | Icons |

## Pages & Routes

```
/login              → LoginPage
/                   → DashboardPage (redirect to /login if not authed)
/portfolio/:id      → PortfolioPage
/budget             → BudgetPage
/bills              → BillsPage
/analytics          → AnalyticsPage
/settings           → SettingsPage
```

All routes except `/login` are protected (redirect to `/login` if no valid session).

## Component Hierarchy

```
App
└── AppShell (layout/AppShell.tsx)
    ├── Sidebar (layout/Sidebar.tsx)
    │   ├── NavItem (each route)
    │   └── LivePriceTicker (shows BTC/ETH/USD price live)
    ├── Topbar (layout/Topbar.tsx)
    │   ├── PageTitle
    │   ├── NotificationBell
    │   └── UserMenu
    └── <Outlet> (page content)
        ├── DashboardPage
        │   ├── KpiCards (total net worth, monthly income, expense, net)
        │   ├── PortfolioMiniCards (one per portfolio)
        │   ├── AllocationPieChart
        │   ├── PortfolioValueChart (30-day line chart)
        │   ├── RecentTransactionsList
        │   └── UpcomingBillsWidget
        ├── PortfolioPage
        │   ├── PortfolioHeader (total value, P&L)
        │   ├── HoldingsTable
        │   │   └── HoldingRow (per asset: current weight vs target, deviation)
        │   ├── AllocationChart (pie + bar comparison: actual vs target)
        │   ├── PortfolioHistoryChart (line chart)
        │   ├── TransactionLog (paginated table)
        │   └── AddTransactionDialog
        ├── BudgetPage
        │   ├── MonthSelector
        │   ├── BudgetSummaryBar (income / expense / net / savings %)
        │   ├── CategoryBreakdown (bar charts)
        │   ├── TransactionList (income + expense tabs)
        │   ├── AddTransactionDialog
        │   ├── MonthlyLogSection (historical summaries table)
        │   └── SnapshotButton (capture month-end log)
        ├── BillsPage
        │   ├── BillsCalendar (show due dates on month calendar)
        │   ├── BillsList
        │   │   └── BillCard (name, amount, due date, status badge, pay button)
        │   └── AddBillDialog
        ├── AnalyticsPage
        │   ├── NetWorthOverTime (combined portfolio history)
        │   ├── SavingsRateTrend (monthly, bar chart)
        │   ├── IncomeVsExpense (monthly comparison)
        │   └── AssetPerformanceTable
        └── SettingsPage
            ├── ProfileSection
            ├── CategoryManager (add/edit/delete income & expense categories)
            ├── AssetManager (add custom assets)
            ├── NotificationPreferences
            └── DangerZone (change password, export data)
```

## State Management

### Zustand stores (client state)

```typescript
// store/auth.store.ts
{
  user: User | null,
  accessToken: string | null,
  setAuth: (user, token) => void,
  clearAuth: () => void
}

// store/prices.store.ts
{
  prices: Record<string, PriceData>,   // live prices from WebSocket
  lastUpdated: Date | null,
  setPrices: (prices) => void,
  updatePrice: (symbol, data) => void
}

// store/ui.store.ts
{
  sidebarOpen: boolean,
  selectedPortfolioId: string | null,
  selectedMonth: string,              // 'YYYY-MM'
  toggleSidebar: () => void,
  setSelectedPortfolio: (id) => void,
  setSelectedMonth: (month) => void
}
```

### React Query (server state)

```typescript
// hooks/usePortfolio.ts
usePortfolios()           // GET /api/portfolios
usePortfolio(id)          // GET /api/portfolios/:id
usePortfolioHistory(id)   // GET /api/portfolios/:id/history
useTransactions(portfolioId) // GET /api/portfolios/:id/transactions
useAddTransaction()       // POST mutation

// hooks/useBudget.ts
useBudgetSummary(month)   // GET /api/budget/summary
useBudgetTransactions(month) // GET /api/budget/transactions
useMonthlySummaries()     // GET /api/budget/summaries
useCategories()           // GET /api/budget/categories
useAddBudgetTransaction() // POST mutation
useSnapshotMonth()        // POST mutation

// hooks/useBills.ts
useBills()                // GET /api/bills
useBillHistory(id)        // GET /api/bills/:id/history
usePayBill()              // POST mutation
useAddBill()              // POST mutation

// hooks/useDashboard.ts
useDashboard()            // GET /api/dashboard

// hooks/usePrices.ts
usePrices()               // GET /api/prices (initial load)
```

### WebSocket (live prices)

```typescript
// hooks/useLivePrices.ts
// Connects to ws://api/ws on mount
// Subscribes to /topic/prices
// On message: calls prices.store.setPrices()
// Components read from prices.store — no prop drilling needed
```

## API Client

```typescript
// api/client.ts
const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });

// Request interceptor: attach Bearer token from auth.store
// Response interceptor:
//   - On 401: call POST /auth/refresh → update tokens → retry request
//   - On repeated 401 after refresh: clear auth, redirect to /login
//   - On 429: show rate limit toast
//   - On 500: show error toast
```

## Design System

**Theme:** Dark mode default (matches the existing Excel dark theme the user has).
Light mode toggle available in settings.

**Color palette (Tailwind + shadcn custom):**
```
Primary:    Blue-600 (#2563eb) — actions, links
Success:    Green-500 (#22c55e) — positive P&L, income
Danger:     Red-500 (#ef4444) — negative P&L, expense
Warning:    Amber-500 (#f59e0b) — upcoming bills, warnings
Muted:      Slate-400 — secondary text
Background: Slate-950 (dark) / White (light)
Surface:    Slate-900 (dark) / Slate-50 (light)
Border:     Slate-800 (dark) / Slate-200 (light)
```

**Typography:**
```
Font: Inter (loaded via Google Fonts or local)
Headings: font-semibold
Body: font-normal
Mono (amounts): font-mono — for financial numbers
```

**Number formatting (utils/formatters.ts):**
```typescript
formatTRY(45000)           → "₺45.000"
formatTRY(45000, true)     → "₺45.000,00" (with cents)
formatPercent(0.0507)      → "%5.07"
formatPercent(-0.045)      → "-%4.50" (red in UI)
formatDate("2026-04-08")   → "8 Nisan 2026"
formatShortDate("2026-04") → "Nis 2026"
```

## Key UX Decisions

1. **Allocation deviation:** Green if within ±1%, yellow if ±1-3%, red if >3%
2. **P&L color:** Always green/red, never neutral
3. **Live price:** Small animated dot + timestamp "5dk önce güncellendi"
4. **Bill status badges:** PENDING=yellow, PAID=green, SKIPPED=gray
5. **Month navigation:** Arrow buttons on BudgetPage to browse historical months
6. **Mobile:** Sidebar collapses to bottom tab bar on mobile
7. **Loading states:** Skeleton loaders on initial load, no spinners
8. **Empty states:** Friendly Turkish messages + CTA button when no data yet
