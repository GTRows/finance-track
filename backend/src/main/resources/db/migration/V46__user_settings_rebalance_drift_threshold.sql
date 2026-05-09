-- Configurable rebalance drift tolerance (Phase 28 sub-plan 02 / G12).
-- Default 1.00 (one percentage point) preserves a tight default for the dashboard
-- preview button. Range 0.10..10.00 covers the realistic spectrum from
-- "rebalance on almost any drift" to "ignore double-digit drift".
ALTER TABLE user_settings
    ADD COLUMN rebalance_drift_threshold_percent NUMERIC(5,2) NOT NULL DEFAULT 1.00
        CHECK (rebalance_drift_threshold_percent BETWEEN 0.10 AND 10.00);
