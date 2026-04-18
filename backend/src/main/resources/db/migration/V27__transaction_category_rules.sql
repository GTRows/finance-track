-- Rule-based auto-categorization. When a user creates or imports a transaction
-- without an explicit category, the service scans its description against each
-- rule's pattern (case-insensitive substring match) and assigns the first
-- matching category. Rules are ordered by priority (ascending), then by
-- creation time, so newer rules sit at the bottom unless promoted.

CREATE TABLE transaction_category_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern      TEXT NOT NULL,
    category_id  UUID NOT NULL,
    txn_type     VARCHAR(10) NOT NULL,
    priority     INTEGER NOT NULL DEFAULT 100,
    match_count  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT transaction_category_rules_type_ck CHECK (txn_type IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX idx_txn_category_rules_user ON transaction_category_rules(user_id, priority, created_at);
