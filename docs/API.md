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
```json
// Response 200
{
  "period": "2026-04",
  "totalIncome": 45000.00,
  "totalExpense": 18500.00,
  "net": 26500.00,
  "savingsRate": 58.89,
  "incomeByCategory": [
    { "categoryName": "Maaş", "amount": 40000.00, "percent": 88.9 }
  ],
  "expenseByCategory": [
    { "categoryName": "Kira", "amount": 8000.00, "percent": 43.2 }
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
Returns income and expense categories.
```json
// Response 200
{
  "income": [{ "id": "uuid", "name": "Maaş", "icon": "wallet", "color": "#22c55e" }],
  "expense": [{ "id": "uuid", "name": "Kira", "icon": "home", "color": "#ef4444", "budgetAmount": 8000 }]
}
```

### POST /api/v1/budget/categories/income
### POST /api/v1/budget/categories/expense
### PUT /api/v1/budget/categories/income/{id}
### DELETE /api/v1/budget/categories/income/{id}

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

## Reports

### GET /api/v1/reports/portfolio/{id}?format=pdf&period=2026-04
Download PDF portfolio report.

### GET /api/v1/reports/budget?format=csv&from=2026-01-01&to=2026-04-30
Download CSV of all transactions.

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
