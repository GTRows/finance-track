# API Contracts

**Base URL:** `/api/v1/v1`
**Auth:** `Authorization: Bearer <accessToken>` on all endpoints except `/auth/*` and `/health`
**Response format:** JSON always
**Error format:**
```json
{
  "error": "Human readable message",
  "code": "SCREAMING_SNAKE_CASE_CODE",
  "requestId": "abc12345",
  "timestamp": "2026-04-08T14:30:00Z",
  "path": "/api/v1/v1/portfolios/123"
}
```

## Auth

### POST /api/v1/auth/register
Create new user account.
```json
// Request
{ "username": "ali", "email": "ali@example.com", "password": "securepass123" }

// Response 201
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessExpiresIn": 900,
  "user": { "id": "uuid", "username": "ali", "email": "ali@example.com", "role": "USER" }
}
```

### POST /api/v1/auth/login
```json
// Request
{ "username": "ali", "password": "securepass123" }

// Response 200 — same as register response when 2FA is disabled.
// When 2FA is enabled the response instead carries a challenge token:
{ "requiresTotp": true, "totpChallengeToken": "eyJ..." }

// Response 401 — invalid credentials
```

### POST /api/v1/auth/2fa/verify
Second step of login when the account has TOTP enabled.
```json
// Request
{ "challengeToken": "eyJ...", "code": "123456" }

// Response 200 — full auth response (accessToken / refreshToken / user)
// Response 401 — invalid code or expired challenge token
```

### GET /api/v1/auth/2fa/status
```json
// Response 200
{ "enabled": true }
```

### POST /api/v1/auth/2fa/setup
Generates a secret and returns provisioning data. Does not enable 2FA yet.
```json
// Response 200
{
  "secret": "JBSWY3DPEHPK3PXP...",
  "otpauthUrl": "otpauth://totp/FinTrack%20Pro:ali?secret=...&issuer=FinTrack%20Pro&..."
}
```

### POST /api/v1/auth/2fa/enable
Confirms the pending secret by verifying a code from the authenticator app.
```json
// Request
{ "code": "123456" }
// Response 204 — TOTP is now active for this user
// Response 400 — code is wrong or no secret has been provisioned
```

### POST /api/v1/auth/2fa/disable
Requires the current password.
```json
// Request
{ "password": "securepass123" }
// Response 204 — secret wiped and TOTP disabled
// Response 401 — wrong password
```

### POST /api/v1/auth/password
Changes the authenticated user's password. All refresh tokens are revoked on success, so every signed-in device must log in again.
```json
// Request
{ "currentPassword": "old-secret", "newPassword": "new-strong-pass" }
// Response 204 — password updated
// Response 400 — PASSWORD_INVALID (wrong current password) or PASSWORD_UNCHANGED
```

### POST /api/v1/auth/sessions/list
Returns every active refresh token for the caller. The optional `refreshToken` in the body flags the current device.
```json
// Request
{ "refreshToken": "eyJ..." }
// Response 200
[
  {
    "id": "uuid",
    "userAgent": "Mozilla/5.0 ...",
    "ipAddress": "192.0.2.1",
    "createdAt": "...",
    "lastUsedAt": "...",
    "expiresAt": "...",
    "current": true
  }
]
```

### DELETE /api/v1/auth/sessions/{id}
Revokes a single session. Response 204.

### POST /api/v1/auth/sessions/revoke-others
Revokes every session except the one identified by the supplied refresh token.
```json
// Request
{ "refreshToken": "eyJ..." }
// Response 200
{ "revoked": 3 }
```

### POST /api/v1/auth/email-verify/confirm
Public. Confirms the email using the token delivered by the verification email.
```json
// Request
{ "token": "abc..." }
// Response 200
{ "status": "verified" }
// Response 400 — EMAIL_TOKEN_INVALID, EMAIL_TOKEN_USED, or EMAIL_TOKEN_EXPIRED
```

### POST /api/v1/auth/email-verify/resend
Re-issues the verification email for the signed-in user. Silent if already verified.
```json
// Response 200
{ "status": "queued" }
```

### POST /api/v1/auth/password-reset/request
Public. Sends a reset link if the email is known. Always 200 to prevent enumeration.
```json
// Request
{ "email": "ali@example.com" }
// Response 200
{ "status": "queued" }
```

### POST /api/v1/auth/password-reset/confirm
Public. Consumes the reset token, sets a new password, and revokes every session.
```json
// Request
{ "token": "abc...", "newPassword": "new-strong-pass" }
// Response 200
{ "status": "reset" }
// Response 400 — RESET_TOKEN_INVALID, RESET_TOKEN_USED, RESET_TOKEN_EXPIRED, or PASSWORD_UNCHANGED
```

### POST /api/v1/auth/refresh
```json
// Request
{ "refreshToken": "eyJ..." }

// Response 200 — new token pair (old refresh token is invalidated)
// Response 401 — expired or revoked token
```

### POST /api/v1/auth/logout
```json
// Request
{ "refreshToken": "eyJ..." }
// Response 204 — token deleted from DB
```

### GET /api/v1/auth/me
```json
// Response 200
{ "id": "uuid", "username": "ali", "email": "ali@example.com", "role": "USER", "createdAt": "..." }
```

---

## Portfolio

### GET /api/v1/portfolios
List all portfolios for the authenticated user.
```json
// Response 200
[
  {
    "id": "uuid",
    "name": "Bireysel Portföy",
    "type": "BIREYSEL",
    "totalValueTry": 165240.00,
    "totalCostTry": 160887.00,
    "pnlTry": 4353.00,
    "pnlPercent": 2.71,
    "holdings": [...]
  }
]
```

### POST /api/v1/portfolios
```json
// Request
{ "name": "Bireysel Portföy", "type": "BIREYSEL", "description": "..." }
// Response 201
```

### GET /api/v1/portfolios/{id}
Full portfolio with holdings detail.
```json
// Response 200
{
  "id": "uuid",
  "name": "Bireysel Portföy",
  "type": "BIREYSEL",
  "totalValueTry": 165240.00,
  "totalCostTry": 160887.00,
  "pnlTry": 4353.00,
  "pnlPercent": 2.71,
  "holdings": [
    {
      "assetSymbol": "BTC",
      "assetName": "Bitcoin",
      "assetType": "CRYPTO",
      "quantity": 0.001234,
      "currentPriceTry": 6788000.00,
      "currentValueTry": 8376.07,
      "avgCostTry": 5900000.00,
      "costBasisTry": 7280.60,
      "pnlTry": 1095.47,
      "pnlPercent": 15.04,
      "targetWeight": 0.05,
      "currentWeight": 0.05069,
      "weightDeviation": 0.00069
    }
  ]
}
```

### PUT /api/v1/portfolios/{portfolioId}/holdings/{holdingId}/pin
Toggle the `pinned` flag on a holding. Pinned holdings float to the top of the
list response and render with a filled star in the UI. Response is the same
`HoldingResponse` shape returned by the holdings list.

### GET /api/v1/portfolios/{id}/history?from=2026-01-01&to=2026-04-08
Historical snapshots for chart.
```json
// Response 200
[
  { "date": "2026-01-01", "totalValueTry": 155000.00 },
  { "date": "2026-01-02", "totalValueTry": 156200.00 }
]
```

### POST /api/v1/portfolios/{id}/transactions
Record a new investment transaction.
```json
// Request
{
  "assetSymbol": "BTC",
  "txnType": "BUY",
  "quantity": 0.001,
  "priceTry": 6800000.00,
  "amountTry": 6800.00,
  "feeTry": 10.00,
  "notes": "DCA alımı",
  "txnDate": "2026-04-08"
}
// Response 201 — updated holding returned
```

### GET /api/v1/portfolios/{id}/transactions
```json
// Query params: ?page=0&size=20&assetSymbol=BTC&txnType=BUY&from=2026-01-01
// Response 200 — paginated list
{
  "content": [...],
  "totalElements": 47,
  "totalPages": 3,
  "page": 0,
  "size": 20
}
```

---

## Budget

### GET /api/v1/budget/transactions
```json
// Query: ?month=2026-04&type=EXPENSE&categoryId=uuid&page=0&size=20
// Response 200 — paginated transactions
```

### POST /api/v1/budget/transactions
```json
// Request
{
  "txnType": "EXPENSE",
  "amount": 450.00,
  "categoryId": "uuid",
  "description": "Migros",
  "txnDate": "2026-04-08",
  "isRecurring": false,
  "tags": ["market", "gıda"]
}
// Response 201
```

### PUT /api/v1/budget/transactions/{id}
### DELETE /api/v1/budget/transactions/{id}

### GET /api/v1/budget/summary?month=2026-04
Each `expenseByCategory` entry carries the category's static `baseBudget`, the
accumulated `rolloverAmount` carried in from prior months of the same year
(only for categories flagged `rolloverEnabled`), and the resulting
`effectiveBudget = baseBudget + rolloverAmount`. Rollover resets to zero in any
month where spending exceeds the running effective budget, matching the
bucket-per-year mental model.
```json
// Response 200
{
  "period": "2026-04",
  "totalIncome": 45000.00,
  "totalExpense": 18500.00,
  "net": 26500.00,
  "savingsRate": 58.89,
  "incomeByCategory": [
    {
      "categoryId": "uuid",
      "categoryName": "Maaş",
      "categoryColor": "#22c55e",
      "amount": 40000.00,
      "percent": 88.9,
      "baseBudget": null,
      "rolloverAmount": null,
      "effectiveBudget": null
    }
  ],
  "expenseByCategory": [
    {
      "categoryId": "uuid",
      "categoryName": "Kira",
      "categoryColor": "#ef4444",
      "amount": 8000.00,
      "percent": 43.2,
      "baseBudget": 8500.00,
      "rolloverAmount": 500.00,
      "effectiveBudget": 9000.00
    }
  ]
}
```

### POST /api/v1/budget/summaries/{period}/snapshot
Capture month-end log snapshot. Saves current month data to `monthly_summaries`.
```json
// Response 201 — saved summary
```

### GET /api/v1/budget/summaries
List all monthly summary logs.
```json
// Response 200
[
  { "period": "2026-04", "totalIncome": 45000, "totalExpense": 18500, "net": 26500, "savingsRate": 58.89 }
]
```

### GET /api/v1/budget/categories
Returns income and expense categories. `rolloverEnabled` is always present
(`false` for income categories, which do not support rollover).
```json
// Response 200
{
  "income": [
    { "id": "uuid", "name": "Maaş", "icon": "wallet", "color": "#22c55e", "budgetAmount": null, "rolloverEnabled": false }
  ],
  "expense": [
    { "id": "uuid", "name": "Kira", "icon": "home", "color": "#ef4444", "budgetAmount": 8500, "rolloverEnabled": true }
  ]
}
```

### POST /api/v1/budget/categories/income
### POST /api/v1/budget/categories/expense
Body supports `name`, `icon`, `color`, `budgetAmount`, and `rolloverEnabled`
(expense only; ignored on income).
```json
{ "name": "Groceries", "icon": "shopping-cart", "color": "#f97316", "budgetAmount": 6000, "rolloverEnabled": true }
```

### PUT /api/v1/budget/categories/income/{id}
### PUT /api/v1/budget/categories/expense/{id}
Same body shape as the POST endpoints. Toggling `rolloverEnabled` back to
`false` stops future rollover accumulation; already-rolled amounts
disappear from the next summary response.

### DELETE /api/v1/budget/categories/income/{id}
### DELETE /api/v1/budget/categories/expense/{id}

### GET /api/v1/budget/recurring
Monthly recurring transaction templates. Server auto-materializes due ones daily at 06:00.
```json
// Response 200
[
  {
    "id": "uuid",
    "txnType": "EXPENSE",
    "amount": 8500.00,
    "categoryId": "uuid",
    "categoryName": "Rent",
    "description": "Monthly rent",
    "dayOfMonth": 5,
    "active": true,
    "lastMaterializedOn": "2026-04-05",
    "nextDueOn": "2026-05-05"
  }
]
```

### POST /api/v1/budget/recurring
Create a template. `dayOfMonth` 1-31; values past month-length fall back to the last day.
```json
{ "txnType": "EXPENSE", "amount": 8500, "categoryId": "uuid", "description": "Rent", "dayOfMonth": 5, "active": true }
```

### PUT /api/v1/budget/recurring/{id}
Replace template fields (same body as create).

### DELETE /api/v1/budget/recurring/{id}
Remove template. Past materialized transactions are untouched.

### POST /api/v1/budget/recurring/{id}/run-now
Immediately materialize one transaction dated today and advance `lastMaterializedOn`.

### GET /api/v1/budget/category-rules
List auto-categorization rules, ordered by `priority` ascending then `createdAt` ascending.
```json
// Response 200
[
  {
    "id": "uuid",
    "pattern": "Migros",
    "categoryId": "uuid",
    "categoryName": "Groceries",
    "categoryColor": "#22c55e",
    "txnType": "EXPENSE",
    "priority": 100,
    "matchCount": 17,
    "createdAt": "2026-04-18T12:00:00Z"
  }
]
```

### POST /api/v1/budget/category-rules
Create a rule. When a new transaction has no `categoryId`, the service matches its
description (case-insensitive substring) against each rule for the same `txnType`
in priority order; the first hit's `categoryId` is assigned and the rule's
`matchCount` is incremented.
```json
{ "pattern": "Migros", "categoryId": "uuid", "txnType": "EXPENSE", "priority": 100 }
```

### PUT /api/v1/budget/category-rules/{id}
Replace rule fields (same body as create).

### DELETE /api/v1/budget/category-rules/{id}
Remove rule. Past transactions already categorized by this rule are not touched.

---

## Bills

### GET /api/v1/bills
```json
// Response 200
[
  {
    "id": "uuid",
    "name": "Netflix",
    "amount": 149.99,
    "dueDay": 15,
    "isActive": true,
    "category": "Abonelik",
    "remindDaysBefore": 3,
    "currentPeriodStatus": "PENDING",
    "currentPeriodDueDate": "2026-04-15",
    "daysUntilDue": 7
  }
]
```

### POST /api/v1/bills
```json
// Request
{
  "name": "Elektrik",
  "amount": 350.00,
  "dueDay": 20,
  "category": "Fatura",
  "remindDaysBefore": 5
}
```

### PUT /api/v1/bills/{id}
### DELETE /api/v1/bills/{id}

### POST /api/v1/bills/{id}/pay
Mark a bill as paid for a period.
```json
// Request
{ "period": "2026-04", "amount": 340.00, "notes": "Biraz düşük geldi" }
// Response 200
```

### GET /api/v1/bills/{id}/history
```json
// Response 200
[
  { "period": "2026-03", "amount": 320.00, "status": "PAID", "paidAt": "2026-03-18T..." },
  { "period": "2026-04", "amount": null, "status": "PENDING", "paidAt": null }
]
```

### POST /api/v1/bills/{id}/mark-used
Stamp `lastUsedOn` with today's date so the subscription audit no longer flags this bill.
```json
// Response 200 -> updated BillResponse with lastUsedOn set
```

### GET /api/v1/bills/audit
Recurring charges that look stale. A bill is a candidate when it is active, created at
least 60 days ago, and either was never marked as used or was last used more than 90 days ago.
```json
// Response 200
{
  "totalMonthlySpend": 1580.00,
  "potentialMonthlySavings": 299.00,
  "candidateCount": 2,
  "candidates": [
    {
      "billId": "uuid",
      "name": "Old streaming bundle",
      "category": "Abonelik",
      "amount": 149.00,
      "currency": "TRY",
      "lastUsedOn": null,
      "daysSinceLastUse": null,
      "reason": "NEVER_USED"
    },
    {
      "billId": "uuid",
      "name": "Forgotten cloud storage",
      "category": "Abonelik",
      "amount": 150.00,
      "currency": "TRY",
      "lastUsedOn": "2026-01-03",
      "daysSinceLastUse": 103,
      "reason": "STALE"
    }
  ]
}
```

---

## Prices

### GET /api/v1/prices
Current prices for all tracked assets.
```json
// Response 200 (also served from Redis cache)
{
  "BTC":  { "symbol": "BTC", "priceTry": 6788000, "priceUsd": 103500, "change24h": -1.2, "updatedAt": "..." },
  "ETH":  { "symbol": "ETH", "priceTry": 98000,   "priceUsd": 1495,  "change24h": -2.1, "updatedAt": "..." },
  "TTA":  { "symbol": "TTA", "priceTry": 1.845,   "priceUsd": null,  "change24h": 0.3,  "updatedAt": "..." },
  "USDTRY": { "symbol": "USDTRY", "priceTry": 65.6, ... }
}
```

### GET /api/v1/prices/{symbol}/history?days=30
Price history for chart (last N days, one entry per day).

### POST /api/v1/prices/sync
Manually trigger price sync (admin only).

---

## Allocation

### GET /api/v1/portfolios/{id}/allocation
Computed drift between the portfolio's target allocation and its live holdings.
```json
// Response 200
{
  "totalValueTry": 165240.50,
  "configured": true,
  "rows": [
    { "assetType": "CRYPTO", "targetPercent": 40.00, "actualPercent": 47.30, "actualValueTry": 78160.40, "driftPercent": 7.30, "driftValueTry": 12062.00 }
  ]
}
```

### PUT /api/v1/portfolios/{id}/allocation
Replace all allocation targets for the portfolio. Sum must equal 100% (tolerance 0.05). An empty list clears all targets.
```json
{
  "targets": [
    { "assetType": "CRYPTO", "targetPercent": 40 },
    { "assetType": "FUND", "targetPercent": 30 },
    { "assetType": "CURRENCY", "targetPercent": 20 },
    { "assetType": "GOLD", "targetPercent": 10 }
  ]
}
```

### GET /api/v1/portfolios/{id}/risk?riskFreeRate=0.15
Risk/return summary derived from the portfolio's daily value snapshots. Returns
`sufficientData: false` until at least 20 daily returns have been captured. All
percentage-style values are decimals (0.12 = 12%). `riskFreeRate` is the annual
risk-free rate used in Sharpe ratio; defaults to 0 when omitted.
```json
// Response 200
{
  "snapshotCount": 142,
  "periodStart": "2025-11-24",
  "periodEnd": "2026-04-16",
  "totalReturn": 0.1834,
  "annualVolatility": 0.2217,
  "sharpeRatio": 1.08,
  "maxDrawdown": -0.0842,
  "bestDay": 0.0361,
  "worstDay": -0.0414,
  "averageDailyReturn": 0.0015,
  "riskFreeRate": 0.15,
  "sufficientData": true
}
```

---

## Watchlist

### GET /api/v1/watchlist
Current user's starred assets, newest first.
```json
// Response 200
[
  { "assetId": "uuid", "note": null, "createdAt": "2026-04-16T10:00:00Z" }
]
```

### POST /api/v1/watchlist
Add or update a watchlist entry. Returns 404 if asset is unknown.
```json
{ "assetId": "uuid", "note": "DCA monthly" }
```

### DELETE /api/v1/watchlist/{assetId}
Remove an asset from the watchlist. Always 204 (idempotent).

---

## Dashboard

### GET /api/v1/dashboard
Aggregated data for the main dashboard.
```json
// Response 200
{
  "totalNetWorth": 217514.00,
  "portfoliosSummary": [
    { "id": "uuid", "name": "Bireysel", "valueTry": 165240, "pnlPercent": 2.71 },
    { "id": "uuid", "name": "BES Prime", "valueTry": 52274, "pnlPercent": 0.52 }
  ],
  "monthlyBudget": {
    "period": "2026-04",
    "income": 45000,
    "expense": 18500,
    "net": 26500,
    "savingsRate": 58.89
  },
  "upcomingBills": [
    { "name": "Netflix", "amount": 149.99, "dueDate": "2026-04-15", "daysUntilDue": 7 }
  ],
  "recentTransactions": [...]
}
```

---

## Net worth

### GET /api/v1/net-worth
Combined daily value series across all active portfolios plus user-authored
annotations. Events are returned newest-first.
```json
// Response 200
{
  "series": [
    { "date": "2026-03-10", "totalValueTry": 195400.00, "totalCostTry": 170000.00 },
    { "date": "2026-03-11", "totalValueTry": 196810.00, "totalCostTry": 170000.00 }
  ],
  "events": [
    {
      "id": "uuid",
      "eventDate": "2026-03-10",
      "eventType": "MILESTONE",
      "label": "Crossed 200K TRY",
      "note": null,
      "impactTry": null
    }
  ]
}
```

### GET /api/v1/net-worth/events
List events only.

### POST /api/v1/net-worth/events
Create a new annotation.
```json
{
  "eventDate": "2026-04-05",
  "eventType": "PURCHASE",
  "label": "Car down-payment",
  "note": "Used CURRENCY bucket",
  "impactTry": -45000
}
```
`eventType` accepts `PURCHASE`, `INCOME`, `MILESTONE`, `NOTE`.

### PUT /api/v1/net-worth/events/{id}
Update an existing annotation. Body identical to POST.

### DELETE /api/v1/net-worth/events/{id}
Always 204.

---

## Savings goals

### GET /api/v1/savings/goals
Active (non-archived) goals with computed progress.
```json
// Response 200
[
  {
    "id": "uuid",
    "name": "Emergency fund",
    "targetAmount": 150000.00,
    "targetDate": "2026-12-31",
    "linkedPortfolioId": "uuid",
    "linkedPortfolioName": "Emergency",
    "notes": null,
    "currentAmount": 84200.00,
    "progressRatio": 0.5613,
    "monthlyPace": 12500.00,
    "projectedCompletion": "2026-09-08",
    "status": "IN_PROGRESS"
  }
]
```
`status` is `IN_PROGRESS` or `REACHED`. When the goal is linked to a portfolio,
`currentAmount` is the live valuation of its holdings; otherwise it is the sum
of contributions. `monthlyPace` and `projectedCompletion` are derived from a
rolling 90-day window and may be `null` when not enough data exists.

### POST /api/v1/savings/goals
```json
// Request
{
  "name": "House down payment",
  "targetAmount": 800000.00,
  "targetDate": "2028-06-30",
  "linkedPortfolioId": "uuid",
  "notes": "Targeting 20% on a 4M TRY apartment"
}
```
`linkedPortfolioId` is optional; when omitted the goal is tracked from manual
contributions.

### PUT /api/v1/savings/goals/{id}
Body identical to POST. Returns the updated goal with recomputed progress.

### DELETE /api/v1/savings/goals/{id}
Soft-archives the goal (sets `archived_at`). Returns 204. Contributions remain
in the database; the goal simply stops appearing in `GET /savings/goals`.

### GET /api/v1/savings/goals/{id}/contributions
Manual contributions, newest-first. Available for both linked and unlinked
goals.

### POST /api/v1/savings/goals/{id}/contributions
```json
{
  "contributionDate": "2026-04-15",
  "amount": 5000.00,
  "note": "Monthly transfer"
}
```

### DELETE /api/v1/savings/goals/{id}/contributions/{contributionId}
Removes a single contribution. Returns 204.

---

## Debts

### GET /api/v1/debts
Active debts with amortization-derived fields.
```json
// Response 200
[
  {
    "id": "uuid",
    "name": "Home mortgage",
    "debtType": "MORTGAGE",
    "principal": 2500000.00,
    "annualRate": 0.1750,
    "termMonths": 240,
    "startDate": "2024-06-01",
    "notes": null,
    "scheduledMonthlyPayment": 39540.12,
    "totalScheduledPaid": 9489628.80,
    "totalActuallyPaid": 890000.00,
    "remainingBalance": 2410860.00,
    "totalInterest": 6989628.80,
    "scheduledPayoffDate": "2044-06-01",
    "projectedPayoffDate": "2044-02-01",
    "progressRatio": 0.0938,
    "monthsAhead": 4,
    "status": "ACTIVE",
    "nextPayments": [
      { "dueDate": "2026-05-01", "payment": 39540.12, "principal": 4368.87, "interest": 35171.25, "remainingBalance": 2406491.13 }
    ]
  }
]
```
`debtType` accepts `MORTGAGE`, `AUTO`, `PERSONAL`, `CREDIT_CARD`, `STUDENT`,
`OTHER`. `annualRate` is a decimal (0.175 = 17.5% APR). `monthsAhead` is
positive when projected payoff is earlier than the scheduled one, negative when
later. `nextPayments` previews the next 6 months of amortization.

### POST /api/v1/debts
```json
{
  "name": "Car loan",
  "debtType": "AUTO",
  "principal": 450000.00,
  "annualRate": 0.22,
  "termMonths": 48,
  "startDate": "2026-01-15",
  "notes": "Zero down"
}
```

### PUT /api/v1/debts/{id}
Same body as POST. Returns the updated debt with recomputed projections.

### DELETE /api/v1/debts/{id}
Soft-archives the debt. Returns 204.

### GET /api/v1/debts/{id}/payments
Payments recorded against the debt, oldest first.

### POST /api/v1/debts/{id}/payments
```json
{
  "paymentDate": "2026-04-01",
  "amount": 40000.00,
  "note": "Regular monthly"
}
```

### DELETE /api/v1/debts/{id}/payments/{paymentId}
Removes a single payment. Returns 204.

---

## FIRE calculator

### GET /api/v1/fire
Computes financial-independence projection. All inputs have defaults derived
from the user's portfolios and monthly summaries; any parameter can be
overridden via query string for what-if scenarios.
```
GET /api/v1/fire?withdrawalRate=0.04&expectedReturn=0.07&monthlyContribution=25000
```
All query params are optional:
- `withdrawalRate` -- safe withdrawal rate (default 0.04)
- `expectedReturn` -- annual real return (default 0.07)
- `monthlyContribution` -- override computed savings ability
- `monthlyExpense` -- override rolling average
- `netWorth` -- override computed live value

```json
// Response 200
{
  "currentNetWorth": 620000.00,
  "avgMonthlyIncome": 85000.00,
  "avgMonthlyExpense": 52000.00,
  "savingsRate": 0.3882,
  "monthlyContribution": 33000.00,
  "withdrawalRate": 0.0400,
  "expectedReturn": 0.0700,
  "targetNumber": 15600000.00,
  "progressRatio": 0.0397,
  "monthsToFi": 234,
  "yearsToFi": 19.50,
  "projectedFiDate": "2045-10-18",
  "samplesUsed": 9,
  "sufficientData": true,
  "trajectory": [
    { "year": 0, "date": "2026-04-18", "netWorth": 620000.00 },
    { "year": 1, "date": "2027-04-18", "netWorth": 1054000.00 }
  ]
}
```
`samplesUsed` is the number of monthly data points the averages were built
from. `sufficientData` flips to `false` below three samples -- the projection
still returns values but the UI should caution the user. `monthsToFi` is
`null` when savings rate plus return cannot close the gap within 60 years.

---

## Tags

Named labels applied to budget transactions through a many-to-many join. Each
tag is owned by a single user and can be attached to any number of income or
expense transactions. Tag assignments are edited through the standard
transaction create/update endpoints by passing `tagIds`.

### GET /api/v1/tags
Returns all tags owned by the caller, alphabetically sorted.
```json
// Response 200
[
  {
    "id": "7b1c...",
    "name": "travel",
    "color": "#0ea5e9",
    "usageCount": 14
  }
]
```
`usageCount` is the number of transactions currently carrying the tag.

### POST /api/v1/tags
```json
// Request
{
  "name": "travel",
  "color": "#0ea5e9"
}

// Response 201 -- same shape as GET items
```
Returns 400 with code `TAG_DUPLICATE` when a tag with the same name already
exists.

### PUT /api/v1/tags/{id}
Renames and/or recolors a tag. Same payload as POST.

### DELETE /api/v1/tags/{id}
Removes the tag and detaches it from every transaction it was linked to.
Returns 204.

### Transaction references
`GET /api/v1/budget/transactions` now accepts an optional `tagId` query
parameter that filters results to transactions carrying that tag. The
transaction response embeds tag references:
```json
{
  "id": "...",
  "tags": [
    { "id": "7b1c...", "name": "travel", "color": "#0ea5e9" }
  ]
}
```
`POST` / `PUT /api/v1/budget/transactions` accept `tagIds: string[]` that
replace (not merge) the tag set on the transaction. Tag ids not owned by the
caller are silently dropped.

---

## Reports

### GET /api/v1/reports/portfolio/{id}?format=pdf&period=2026-04
Download PDF portfolio report.

### GET /api/v1/reports/budget?from=2026-01-01&to=2026-04-30
Download CSV of all transactions in the range.

### GET /api/v1/reports/budget/xlsx?from=2026-01-01&to=2026-04-30
Download an Excel (`.xlsx`) export of the same data as the CSV endpoint. The
workbook has a "Transactions" sheet with header row frozen, auto-filters
enabled, a date-formatted column, a `#,##0.00` amount column, and tags
collapsed into a semicolon-separated string.

---

## AI Analysis (optional — only if CLAUDE_ENABLED=true)

### POST /api/v1/ai/analyze
```json
// Request
{
  "analysisType": "PORTFOLIO_REVIEW" | "BUDGET_REVIEW" | "REBALANCE_SUGGESTION",
  "context": "optional extra instructions from user"
}

// Response 200 — streaming text response
{ "analysis": "Bu ay portföyünüzde..." }
```

---

## Health

### GET /api/v1/health
```json
// Response 200 (no auth required)
{ "status": "UP", "version": "1.0.0", "timestamp": "..." }
```

---

## Common Patterns

**Pagination:** `?page=0&size=20` on all list endpoints.
Response wraps in `{ content: [...], totalElements: N, totalPages: N, page: N, size: N }`.

**Date format:** ISO 8601. Dates: `YYYY-MM-DD`. Timestamps: `YYYY-MM-DDTHH:mm:ssZ`.

**Error responses:**
```json
{ "error": "Portfolio not found", "code": "PORTFOLIO_NOT_FOUND", "timestamp": "..." }
```

**HTTP status codes:**
- 200 OK, 201 Created, 204 No Content
- 400 Bad Request (validation error)
- 401 Unauthorized (missing/invalid token)
- 403 Forbidden (valid token, wrong user)
- 404 Not Found
- 429 Too Many Requests (rate limited)
- 500 Internal Server Error
