-- Bank CSV import idempotency fingerprint (Phase 27 sub-plan 04 / G3).
-- Adds a deterministic SHA-256 hex digest of (account_id, date, signed_amount,
-- balance_after, counterparty_hash) so re-uploading the same statement file
-- is a no-op. Existing rows stay at NULL (no fingerprint -- pre-27-04 imports
-- and manual entries). Composite unique index is partial so NULLs do not
-- collide and pre-27-04 rows do not need backfill.

ALTER TABLE transactions
    ADD COLUMN import_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_transactions_import_fingerprint_account
    ON transactions (account_id, import_fingerprint)
    WHERE import_fingerprint IS NOT NULL;
