-- Accounts (Phase 27 sub-plan 02). Standalone owner-scoped declaration of
-- where value sits: bank checking/savings, brokerage cash, crypto wallets,
-- physical cash. Independent of portfolios and transactions in this plan;
-- 27-03 wires investment / budget transactions to account_id.
CREATE TABLE accounts (
    id                       UUID PRIMARY KEY,
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                     VARCHAR(100) NOT NULL,
    account_type             VARCHAR(30) NOT NULL,
    currency                 VARCHAR(3) NOT NULL,
    institution              VARCHAR(100),
    -- Trailing digits only (last 4-8). Never store the full PAN.
    account_number_suffix    VARCHAR(16),
    notes                    TEXT,
    current_balance          NUMERIC(20, 8) NOT NULL DEFAULT 0,
    is_archived              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Live-rows-only uniqueness on (user_id, lower(name)) so an archived
-- "Main" does not block a new live "main".
CREATE UNIQUE INDEX uq_accounts_user_name_live
    ON accounts (user_id, lower(name))
    WHERE is_archived = FALSE;

-- Hot read path: list-by-user-and-not-archived.
CREATE INDEX idx_accounts_user_archived ON accounts (user_id, is_archived);
