-- Index audit (Phase 15.5). Only the indexes whose motivating query has
-- measurable call volume are added here; composite and partial indexes
-- already cover most other hot paths.

-- (1) Rollover summary and budget-rule evaluation both call
--     sumByUserIdAndCategoryAndDateRange(userId, categoryId, from, to).
--     computeRollovers() loops O(monthsInYear * rolloverCategories), so this
--     query is the hottest in the app. The existing (user_id, txn_date)
--     composite forces Postgres to scan every txn in the date window and
--     filter category_id in-memory. Adding (user_id, category_id, txn_date)
--     lets the planner use a single covered index scan.
CREATE INDEX idx_transactions_user_category_date
    ON transactions(user_id, category_id, txn_date);

-- (2) Category-rule lookup filters by (user_id, txn_type) ordered by
--     priority, created_at. The V27 index on (user_id, priority, created_at)
--     covers the unsorted list, but incoming transactions with no category
--     evaluate rules per-create via findByUserIdAndTxnTypeOrderBy..., so a
--     tighter composite matching that exact filter pays off.
CREATE INDEX idx_txn_category_rules_user_type
    ON transaction_category_rules(user_id, txn_type, priority, created_at);
