-- ============================================================
-- FinTrack Pro -- V1 Initial Schema
-- All tables, indexes, constraints. No seed data.
-- ============================================================

-- Users
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Refresh tokens (server-side, revocable)
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ============================================================
-- Assets (trackable financial instruments)
-- ============================================================
CREATE TABLE assets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol           VARCHAR(20) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    asset_type       VARCHAR(30) NOT NULL,
    currency         VARCHAR(10) NOT NULL DEFAULT 'TRY',
    price            NUMERIC(20, 6),
    price_usd        NUMERIC(20, 6),
    price_updated_at TIMESTAMPTZ,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(symbol, asset_type)
);

-- ============================================================
-- Portfolios
-- ============================================================
CREATE TABLE portfolios (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    portfolio_type  VARCHAR(30) NOT NULL DEFAULT 'BIREYSEL',
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_portfolios_user ON portfolios(user_id);

-- Portfolio holdings (current state)
CREATE TABLE portfolio_holdings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id        UUID NOT NULL REFERENCES assets(id),
    quantity        NUMERIC(20, 8) NOT NULL DEFAULT 0,
    avg_cost_try    NUMERIC(20, 4),
    target_weight   NUMERIC(5, 4),
    notes           TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(portfolio_id, asset_id)
);

-- Investment transactions
CREATE TABLE investment_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id        UUID NOT NULL REFERENCES assets(id),
    txn_type        VARCHAR(30) NOT NULL,
    quantity        NUMERIC(20, 8),
    price_try       NUMERIC(20, 4),
    amount_try      NUMERIC(20, 4) NOT NULL,
    fee_try         NUMERIC(20, 4) DEFAULT 0,
    notes           TEXT,
    txn_date        DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inv_txns_portfolio ON investment_transactions(portfolio_id);
CREATE INDEX idx_inv_txns_date ON investment_transactions(txn_date);
CREATE INDEX idx_inv_txns_asset ON investment_transactions(asset_id);

-- Portfolio daily snapshots (for chart history)
CREATE TABLE portfolio_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    snapshot_date   DATE NOT NULL,
    total_value_try NUMERIC(20, 4) NOT NULL,
    total_cost_try  NUMERIC(20, 4),
    holdings_json   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(portfolio_id, snapshot_date)
);
CREATE INDEX idx_snapshots_portfolio_date ON portfolio_snapshots(portfolio_id, snapshot_date);

-- ============================================================
-- Income & Expenses
-- ============================================================
CREATE TABLE income_categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    icon       VARCHAR(50),
    color      VARCHAR(7),
    is_default BOOLEAN DEFAULT FALSE
);

CREATE TABLE expense_categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    icon          VARCHAR(50),
    color         VARCHAR(7),
    budget_amount NUMERIC(12, 2),
    is_default    BOOLEAN DEFAULT FALSE
);

CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    txn_type        VARCHAR(10) NOT NULL CHECK (txn_type IN ('INCOME', 'EXPENSE')),
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'TRY',
    category_id     UUID,
    description     VARCHAR(255),
    notes           TEXT,
    txn_date        DATE NOT NULL,
    is_recurring    BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_rule VARCHAR(100),
    tags            TEXT[],
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, txn_date);
CREATE INDEX idx_transactions_type ON transactions(user_id, txn_type);

-- Monthly budget log (snapshot at month end)
CREATE TABLE monthly_summaries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period          VARCHAR(7) NOT NULL,
    total_income    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_expense   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    net             NUMERIC(12, 2) GENERATED ALWAYS AS (total_income - total_expense) STORED,
    savings_rate    NUMERIC(5, 2),
    notes           TEXT,
    snapshot_json   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, period)
);

-- ============================================================
-- Bills (recurring bills with reminders)
-- ============================================================
CREATE TABLE bills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'TRY',
    category            VARCHAR(50),
    due_day             INTEGER NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    auto_pay            BOOLEAN NOT NULL DEFAULT FALSE,
    remind_days_before  INTEGER NOT NULL DEFAULT 3,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bills_user ON bills(user_id);

-- Bill payment history
CREATE TABLE bill_payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id     UUID NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    period      VARCHAR(7) NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    paid_at     TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(bill_id, period)
);
CREATE INDEX idx_bill_payments_bill ON bill_payments(bill_id);
CREATE INDEX idx_bill_payments_period ON bill_payments(period);

-- ============================================================
-- Price history (for charts, kept 90 days)
-- ============================================================
CREATE TABLE price_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id    UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    price       NUMERIC(20, 6) NOT NULL,
    price_usd   NUMERIC(20, 6),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_price_history_asset_time ON price_history(asset_id, recorded_at DESC);

-- ============================================================
-- User settings / preferences (1:1 with users)
-- ============================================================
CREATE TABLE user_settings (
    user_id                  UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    currency                 VARCHAR(10) NOT NULL DEFAULT 'TRY',
    language                 VARCHAR(5) NOT NULL DEFAULT 'tr',
    theme                    VARCHAR(10) NOT NULL DEFAULT 'dark',
    dashboard_layout         JSONB,
    notification_preferences JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
