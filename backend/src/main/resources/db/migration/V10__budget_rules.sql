-- ============================================================
-- V10: Budget rules and generalized notifications.
--
-- - Generalize alert_notifications so other event types
--   (budget rules) can write to the same notifications stream
--   that powers the in-app bell.
-- - Add budget_rules: a per-category monthly TRY ceiling that
--   raises an alert the first time monthly spend crosses it.
-- ============================================================

ALTER TABLE alert_notifications
    ALTER COLUMN alert_id DROP NOT NULL,
    ALTER COLUMN asset_id DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'PRICE_ALERT'
        CHECK (source_type IN ('PRICE_ALERT', 'BUDGET_RULE')),
    ADD COLUMN source_id UUID;

UPDATE alert_notifications SET source_id = alert_id WHERE source_id IS NULL;

CREATE TABLE budget_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id         UUID NOT NULL REFERENCES expense_categories(id) ON DELETE CASCADE,
    monthly_limit_try   NUMERIC(20, 6) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    last_alerted_period VARCHAR(7),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id)
);
CREATE INDEX idx_budget_rules_user ON budget_rules(user_id);
