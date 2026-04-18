-- Multi-currency transactions (Phase 10.2). The existing amount/currency pair
-- continues to hold the user's home-currency value (what every aggregate sums
-- against), while original_amount/original_currency preserve the value the
-- user actually entered when the two differ. Null means the transaction was
-- already entered in the home currency and no conversion happened.
ALTER TABLE transactions
    ADD COLUMN original_amount NUMERIC(12, 2),
    ADD COLUMN original_currency VARCHAR(10);
