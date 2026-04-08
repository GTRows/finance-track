# Database Schema

**Engine:** PostgreSQL 16
**Migration tool:** Flyway — migration files in `backend/src/main/resources/db/migration/`
**Naming convention:** `V{n}__{description}.sql` (e.g. `V1__initial_schema.sql`)

Never edit existing migration files. Always add a new `V{n+1}__*.sql` for changes.

## Entity Relationship Overview

```
users
  ├── portfolios (1:many)
  │     ├── portfolio_holdings (1:many) → assets
  │     ├── investment_transactions (1:many) → assets
  │     └── portfolio_snapshots (1:many)
  ├── budget_transactions (1:many) → income_categories / expense_categories
  ├── monthly_summaries (1:many)
  ├── bills (1:many)
  │     └── bill_payments (1:many)
  └── user_settings (1:1)

assets
  ├── portfolio_holdings (1:many)
  ├── investment_transactions (1:many)
  └── price_history (1:many)
```

## Tables

### users
Primary identity table. Single user for now, multi-user ready.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | gen_random_uuid() |
| username | VARCHAR(50) UNIQUE | login identifier |
| email | VARCHAR(255) UNIQUE | |
| password | VARCHAR(255) | BCrypt hash, strength 12 |
| role | VARCHAR(20) | USER \| ADMIN |
| is_active | BOOLEAN | soft disable |
| created_at | TIMESTAMPTZ | auto |
| updated_at | TIMESTAMPTZ | auto |

### refresh_tokens
Server-side refresh token storage. Enables revocation (logout from all devices).

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | CASCADE DELETE |
| token | VARCHAR(512) UNIQUE | raw token value |
| expires_at | TIMESTAMPTZ | checked on every refresh |
| created_at | TIMESTAMPTZ | |

Index: `token` (lookup), `user_id` (revoke all for user)

### assets
Master list of trackable financial instruments. Seeded with Turkish portfolio assets.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| symbol | VARCHAR(20) | BTC, ETH, TTA, ITP, TIE, TMG, TI1, ABE, AH5, BHT, BGL, AH3, USD, EUR |
| name | VARCHAR(100) | human readable |
| asset_type | VARCHAR(30) | CRYPTO \| STOCK \| GOLD \| FUND \| CURRENCY \| OTHER |
| currency | VARCHAR(10) | native currency: USD for crypto, TRY for funds |
| price | NUMERIC(20,6) | latest price in native currency |
| price_usd | NUMERIC(20,6) | latest price in USD (null if not applicable) |
| price_updated_at | TIMESTAMPTZ | when price was last synced |
| metadata | JSONB | market_cap, volume, 7d_change, etc. |
| created_at | TIMESTAMPTZ | |

Unique: `(symbol, asset_type)` — same symbol can exist as STOCK and FUND

### portfolios
Container for a group of investments. A user can have multiple portfolios.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | |
| name | VARCHAR(100) | "Bireysel Portföy", "BES Prime" |
| portfolio_type | VARCHAR(30) | BIREYSEL \| BES |
| description | TEXT | optional |
| is_active | BOOLEAN | |
| created_at | TIMESTAMPTZ | |

### portfolio_holdings
Current position in each asset within a portfolio.
Updated on every investment transaction.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| portfolio_id | UUID FK → portfolios | CASCADE DELETE |
| asset_id | UUID FK → assets | |
| quantity | NUMERIC(20,8) | current units held |
| avg_cost_try | NUMERIC(20,4) | weighted average cost in TRY |
| target_weight | NUMERIC(5,4) | 0.45 = 45% target allocation |
| notes | TEXT | e.g. "BES için DK oranı %20" |
| updated_at | TIMESTAMPTZ | |

Unique: `(portfolio_id, asset_id)` — one holding per asset per portfolio

**Calculated fields (not stored, computed in service):**
- `current_value_try` = `quantity × asset.price` (converted to TRY)
- `current_weight` = `current_value_try / portfolio_total_try`
- `weight_deviation` = `current_weight - target_weight`
- `pnl_try` = `current_value_try - (quantity × avg_cost_try)`
- `pnl_percent` = `pnl_try / (quantity × avg_cost_try)`

### investment_transactions
Full audit log of every portfolio action.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| portfolio_id | UUID FK → portfolios | |
| asset_id | UUID FK → assets | |
| txn_type | VARCHAR(30) | BUY \| SELL \| DEPOSIT \| WITHDRAW \| REBALANCE \| BES_CONTRIBUTION |
| quantity | NUMERIC(20,8) | units bought/sold (null for DEPOSIT/WITHDRAW) |
| price_try | NUMERIC(20,4) | price per unit at time of transaction |
| amount_try | NUMERIC(20,4) | total TRY amount |
| fee_try | NUMERIC(20,4) | transaction fee |
| notes | TEXT | |
| txn_date | DATE | |
| created_at | TIMESTAMPTZ | |

Indexes: `portfolio_id`, `txn_date`, `asset_id`

### portfolio_snapshots
Daily point-in-time portfolio valuation. Used for historical charts.
Created by a nightly scheduler job.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| portfolio_id | UUID FK → portfolios | |
| snapshot_date | DATE | |
| total_value_try | NUMERIC(20,4) | total portfolio value |
| total_cost_try | NUMERIC(20,4) | total invested amount |
| holdings_json | JSONB | full holding breakdown at this date |
| created_at | TIMESTAMPTZ | |

Unique: `(portfolio_id, snapshot_date)` — one snapshot per day per portfolio

### income_categories / expense_categories
User-defined categories for income and expenses.
Pre-seeded with defaults, fully customizable.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | |
| name | VARCHAR(100) | "Maaş", "Kira", "Market" |
| icon | VARCHAR(50) | icon name from lucide-react |
| color | VARCHAR(7) | hex color for charts |
| budget_amount | NUMERIC(12,2) | monthly budget limit (expense_categories only) |
| is_default | BOOLEAN | can't be deleted if true |

### budget_transactions (also called `transactions`)
Every income or expense entry.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | |
| txn_type | VARCHAR(10) | INCOME \| EXPENSE |
| amount | NUMERIC(12,2) | |
| currency | VARCHAR(10) | TRY default |
| category_id | UUID | FK to income_ or expense_categories |
| description | VARCHAR(255) | "Nisan maaşı", "Migros alışveriş" |
| notes | TEXT | |
| txn_date | DATE | |
| is_recurring | BOOLEAN | |
| recurrence_rule | VARCHAR(100) | RRULE format (e.g. FREQ=MONTHLY) |
| tags | TEXT[] | PostgreSQL array |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

Indexes: `(user_id, txn_date)`, `(user_id, txn_type)`

### monthly_summaries
Month-end snapshots. The "log" the user captures each month.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | |
| period | VARCHAR(7) | 'YYYY-MM' e.g. '2026-04' |
| total_income | NUMERIC(12,2) | |
| total_expense | NUMERIC(12,2) | |
| net | NUMERIC(12,2) | GENERATED: income - expense |
| savings_rate | NUMERIC(5,2) | percentage |
| notes | TEXT | |
| snapshot_json | JSONB | full category breakdown at snapshot time |
| created_at | TIMESTAMPTZ | |

Unique: `(user_id, period)`

### bills
Recurring bills to track and pay monthly.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| user_id | UUID FK → users | |
| name | VARCHAR(100) | "Netflix", "Kira", "Elektrik" |
| amount | NUMERIC(12,2) | expected amount |
| currency | VARCHAR(10) | TRY default |
| category | VARCHAR(50) | free text category |
| due_day | INTEGER (1-31) | day of month payment is due |
| is_active | BOOLEAN | |
| auto_pay | BOOLEAN | mark paid automatically |
| remind_days_before | INTEGER | send reminder N days before due |
| notes | TEXT | |
| created_at | TIMESTAMPTZ | |

### bill_payments
Payment record for each bill per month.
Auto-generated at month start by scheduler.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| bill_id | UUID FK → bills | CASCADE DELETE |
| period | VARCHAR(7) | 'YYYY-MM' |
| amount | NUMERIC(12,2) | actual amount paid (may differ from bill.amount) |
| paid_at | TIMESTAMPTZ | when payment was recorded |
| status | VARCHAR(20) | PENDING \| PAID \| SKIPPED |
| notes | TEXT | |
| created_at | TIMESTAMPTZ | |

Unique: `(bill_id, period)` — one payment record per bill per month

### price_history
Time-series price data. Kept for 90 days (older data cleaned by scheduler).

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| asset_id | UUID FK → assets | CASCADE DELETE |
| price | NUMERIC(20,6) | price in native currency |
| price_usd | NUMERIC(20,6) | USD equivalent |
| recorded_at | TIMESTAMPTZ | |

Index: `(asset_id, recorded_at DESC)` — optimized for "last N prices for asset X"

### user_settings
Per-user preferences.

| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID PK FK → users | 1:1 |
| currency | VARCHAR(10) | display currency, default TRY |
| language | VARCHAR(5) | tr \| en |
| theme | VARCHAR(10) | dark \| light |
| dashboard_layout | JSONB | widget positions/visibility |
| notification_preferences | JSONB | email/ws notification toggles |

## Flyway Migration Files

```
db/migration/
├── V1__initial_schema.sql     # All tables above
├── V2__seed_assets.sql        # Pre-insert known assets (BTC, ETH, TTA, etc.)
├── V3__seed_categories.sql    # Default income/expense categories in Turkish
└── (future migrations here)
```

## Redis Key Design

```
price:{symbol}              → JSON price object, TTL 5min
session:{userId}            → session data, TTL = access token expiry
rate:auth:{ip}              → counter, TTL 1min (auth rate limiting)
rate:api:{ip}               → counter, TTL 1min (general rate limiting)
rt:blacklist:{token_hash}   → exists = revoked, TTL = token expiry
```
