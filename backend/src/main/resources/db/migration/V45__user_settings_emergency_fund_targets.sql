-- Configurable target months for the emergency-fund coverage tile (Phase 28 sub-plan 01).
-- Defaults preserve the V43 behaviour: red < 3, amber 3..6, green > 6.
-- Single-column CHECK clauses document intent; the cross-column invariant
-- amber_floor_months < target_months is enforced at the SettingsService layer
-- (matches the project precedent for cross-row / cross-column rules).
ALTER TABLE user_settings
    ADD COLUMN emergency_fund_target_months SMALLINT NOT NULL DEFAULT 6
        CHECK (emergency_fund_target_months BETWEEN 2 AND 24);

ALTER TABLE user_settings
    ADD COLUMN emergency_fund_amber_floor_months SMALLINT NOT NULL DEFAULT 3
        CHECK (emergency_fund_amber_floor_months BETWEEN 1 AND 23);
