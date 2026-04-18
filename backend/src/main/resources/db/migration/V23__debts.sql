CREATE TABLE debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    debt_type VARCHAR(20) NOT NULL,
    principal NUMERIC(20, 2) NOT NULL CHECK (principal > 0),
    annual_rate NUMERIC(7, 4) NOT NULL CHECK (annual_rate >= 0),
    term_months INTEGER NOT NULL CHECK (term_months > 0),
    start_date DATE NOT NULL,
    notes TEXT,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_debts_user_active ON debts (user_id) WHERE archived_at IS NULL;

CREATE TABLE debt_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_id UUID NOT NULL REFERENCES debts(id) ON DELETE CASCADE,
    payment_date DATE NOT NULL,
    amount NUMERIC(20, 2) NOT NULL CHECK (amount > 0),
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_debt_payments_debt_date ON debt_payments (debt_id, payment_date DESC);
