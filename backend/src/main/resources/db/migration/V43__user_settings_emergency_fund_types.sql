-- Emergency-fund coverage tile inclusion types (Phase 27 sub-plan 03).
-- Stored as a JSONB array of Account.AccountType enum names. Default is
-- ["BANK_SAVINGS"]; the dashboard tile lets the operator toggle BANK_CHECKING
-- and CASH on/off. BROKERAGE_CASH / CRYPTO_WALLET / OTHER are intentionally
-- not included by the UI (liquidity profile mismatches).
ALTER TABLE user_settings
    ADD COLUMN emergency_fund_include_types JSONB
        NOT NULL DEFAULT '["BANK_SAVINGS"]'::jsonb;
