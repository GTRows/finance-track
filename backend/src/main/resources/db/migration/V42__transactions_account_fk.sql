-- Transactions linked to accounts (Phase 27 sub-plan 03 / G2-b).
-- Adds a nullable account_id FK to every transaction-bearing entity so
-- Account.currentBalance can be recomputed via the @TransactionalEventListener
-- pattern from 25-01. Existing rows stay at NULL ("out-of-band" -- does not
-- move any account balance). Operator attaches accounts going forward via
-- the create/edit form. ON DELETE SET NULL so a hard-archived (future GDPR
-- purge) account does not break historical transaction rows.

ALTER TABLE transactions
    ADD COLUMN account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE investment_transactions
    ADD COLUMN account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE bill_payments
    ADD COLUMN account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;

-- Hot read paths for the @TransactionalEventListener rollup. Partial index
-- skips the bulk of pre-27-03 rows that sit at account_id IS NULL.
CREATE INDEX idx_transactions_account_id
    ON transactions (account_id) WHERE account_id IS NOT NULL;
CREATE INDEX idx_investment_transactions_account_id
    ON investment_transactions (account_id) WHERE account_id IS NOT NULL;
CREATE INDEX idx_bill_payments_account_id
    ON bill_payments (account_id) WHERE account_id IS NOT NULL;
