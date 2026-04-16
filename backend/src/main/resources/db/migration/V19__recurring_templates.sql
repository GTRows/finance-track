CREATE TABLE recurring_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    txn_type VARCHAR(10) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    category_id UUID,
    description VARCHAR(255),
    day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_materialized_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_templates_user ON recurring_templates(user_id);
CREATE INDEX idx_recurring_templates_active ON recurring_templates(active) WHERE active = TRUE;
