# 30-02 Inspection — FK column index coverage

Transient discovery file. Deleted by Task 6 of 30-02.

## FK column inventory across V1..V46

Format: `table.column -> references_table.column [ON DELETE ...]`. Decision = INDEXED-ALREADY (which leading-column index covers it) | NEEDS-INDEX (added by V47) | SKIP (justified).

### V1 (initial schema)

- `refresh_tokens.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_refresh_tokens_user`).
- `portfolios.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_portfolios_user`).
- `portfolio_holdings.portfolio_id -> portfolios.id [CASCADE]` — INDEXED-ALREADY (leading column of the `UNIQUE(portfolio_id, asset_id)` composite).
- `portfolio_holdings.asset_id -> assets.id [no action]` — **NEEDS-INDEX**. The unique composite leads with `portfolio_id`; an `asset_id`-only equality lookup falls back to a sequential scan. Repository: `HoldingRepository.findByPortfolioIdAndAssetId` (the second predicate). Asset deletion does not cascade here, so the parent-delete reverse scan does not apply, but `asset_id` IS used in `findByPortfolioIdAndAssetId`.
- `investment_transactions.portfolio_id -> portfolios.id [CASCADE]` — INDEXED-ALREADY (`idx_inv_txns_portfolio`).
- `investment_transactions.asset_id -> assets.id [no action]` — INDEXED-ALREADY (`idx_inv_txns_asset`).
- `portfolio_snapshots.portfolio_id -> portfolios.id [CASCADE]` — INDEXED-ALREADY (`idx_snapshots_portfolio_date` leading column).
- `income_categories.user_id -> users.id [CASCADE]` — NOT INDEXED, but parent-delete is the only write path that hits this table; at single-user scale the table holds < 30 rows. SKIP and document.
- `expense_categories.user_id -> users.id [CASCADE]` — same as `income_categories`. SKIP.
- `transactions.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_transactions_user_date` + `idx_transactions_type` + `idx_transactions_user_category_date` lead with `user_id`).
- `monthly_summaries.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`UNIQUE(user_id, period)` leading column).
- `bills.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_bills_user`).
- `bill_payments.bill_id -> bills.id [CASCADE]` — INDEXED-ALREADY (`idx_bill_payments_bill` + the `UNIQUE(bill_id, period)` composite).
- `price_history.asset_id -> assets.id [CASCADE]` — INDEXED-ALREADY (`idx_price_history_asset_time` leading column).
- `user_settings.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (PRIMARY KEY).

### V9 (price alerts)

- `price_alerts.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_price_alerts_user`).
- `price_alerts.asset_id -> assets.id [CASCADE]` — **NEEDS-INDEX**. Asset deletion cascades to `price_alerts` and there is no index on `asset_id`. Reverse scan today is sequential.
- `alert_notifications.alert_id -> price_alerts.id [CASCADE]` — **NEEDS-INDEX**. Alert deletion cascades to notifications; no index on `alert_id`.
- `alert_notifications.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_alert_notifications_user_time` + `idx_alert_notifications_unread` lead with `user_id`).
- `alert_notifications.asset_id -> assets.id [CASCADE]` — **NEEDS-INDEX**. Asset deletion cascades to notifications; no index on `asset_id`.

### V10 (budget rules)

- `budget_rules.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_budget_rules_user`).
- `budget_rules.category_id -> expense_categories.id [CASCADE]` — INDEXED-ALREADY (`UNIQUE(user_id, category_id)` is leading-`user_id` so does NOT cover category_id alone, BUT category cascades from `expense_categories` are bounded by the small per-user table; SKIP and document).

### V14 (email verification)

- `email_verifications.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_email_verifications_user`).

### V15 (password reset)

- `password_resets.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_password_resets_user`).

### V17 (watchlist)

- `watchlist_entries.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_watchlist_user`).
- `watchlist_entries.asset_id -> assets.id [CASCADE]` — INDEXED-ALREADY (`UNIQUE(user_id, asset_id)` does NOT cover asset_id alone; but watchlist is small AND asset-cascade reverse scan is bounded by the asset master being deleted exclusively by the operator. SKIP and document.

### V18 (portfolio_allocation_targets)

- `portfolio_allocation_targets.portfolio_id -> portfolios.id [CASCADE]` — INDEXED-ALREADY (`idx_allocation_targets_portfolio`).

### V19 (recurring_templates)

- `recurring_templates.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_recurring_templates_user`).
- `recurring_templates.category_id` — soft FK (not declared with REFERENCES). Column is nullable + unindexed. SKIP — no declared FK to chase a parent-delete reverse scan, and no repository method filters by category_id alone.

### V21 (net_worth_events)

- `net_worth_events.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_net_worth_events_user_date` leading column).

### V22 (savings_goals)

- `savings_goals.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_savings_goals_user_active` leading column).
- `savings_goals.linked_portfolio_id -> portfolios.id [SET NULL]` — **NEEDS-INDEX**. Portfolio deletion sets this column to NULL on every linked goal; reverse scan is sequential today. Partial `WHERE linked_portfolio_id IS NOT NULL` keeps the index byte-tight (most goals are unlinked).
- `savings_goal_contributions.goal_id -> savings_goals.id [CASCADE]` — INDEXED-ALREADY (`idx_savings_contributions_goal_date` leading column).

### V23 (debts)

- `debts.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_debts_user_active` leading column).
- `debt_payments.debt_id -> debts.id [CASCADE]` — INDEXED-ALREADY (`idx_debt_payments_debt_date` leading column).

### V24 (transaction_tags)

- `tags.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_tags_user`).
- `transaction_tags.transaction_id -> transactions.id [CASCADE]` — INDEXED-ALREADY (PRIMARY KEY `(transaction_id, tag_id)` leading column).
- `transaction_tags.tag_id -> tags.id [CASCADE]` — INDEXED-ALREADY (`idx_transaction_tags_tag`).

### V27 (transaction_category_rules)

- `transaction_category_rules.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_txn_category_rules_user` + `idx_txn_category_rules_user_type` lead with `user_id`).
- `transaction_category_rules.category_id` — soft FK (not declared with REFERENCES). SKIP.

### V31 (cash_flow_allocator)

- `allocation_buckets.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_allocation_buckets_user` leading column).
- `allocation_buckets.category_id` — soft FK (not declared with REFERENCES). SKIP.

### V32 (push_subscriptions)

- `push_subscriptions.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_push_subscriptions_user`).

### V34 (dividends)

- `dividends.portfolio_id -> portfolios.id [CASCADE]` — INDEXED-ALREADY (`idx_dividends_portfolio` leading column).
- `dividends.asset_id -> assets.id [CASCADE]` — INDEXED-ALREADY (`idx_dividends_asset` leading column on `(asset_id, payment_date DESC)`).

### V35 (totp_recovery_codes)

- `totp_recovery_codes.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_totp_recovery_codes_user`).

### V38 (authenticators)

- `authenticators.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`idx_authenticators_user_id`).
- `authenticators.credential_id` is `BYTEA NOT NULL UNIQUE` (NOT a foreign key — UNIQUE auto-creates an index covering equality lookups; no extra index needed). NOT a FK column.

### V41 (accounts)

- `accounts.user_id -> users.id [CASCADE]` — INDEXED-ALREADY (`uq_accounts_user_name_live` partial unique + `idx_accounts_user_archived` lead with `user_id`).

### V42 (transactions_account_fk)

- `transactions.account_id -> accounts.id [SET NULL]` — INDEXED-ALREADY (`idx_transactions_account_id` partial WHERE NOT NULL).
- `investment_transactions.account_id -> accounts.id [SET NULL]` — INDEXED-ALREADY (`idx_investment_transactions_account_id` partial WHERE NOT NULL).
- `bill_payments.account_id -> accounts.id [SET NULL]` — INDEXED-ALREADY (`idx_bill_payments_account_id` partial WHERE NOT NULL).

## ORDER BY hot-column audit

- `transactions.txn_date` — INDEXED-ALREADY (V1 `idx_transactions_user_date` + V28 `idx_transactions_user_category_date`).
- `bills.due_day` — small bills set per user (< 30 rows). SKIP — sequential scan is sub-millisecond.
- `audit_log.created_at` — INDEXED-ALREADY (V12 `idx_audit_log_created_at` + composites).
- `bill_payments.period` — INDEXED-ALREADY (V1 `idx_bill_payments_period`).
- `dividends.payment_date` — INDEXED-ALREADY (composite `(portfolio_id, payment_date DESC)` + `(asset_id, payment_date DESC)`).
- `assets.symbol` — `AssetRepository.findAllByOrderBySymbolAsc`. Asset master is ≤ ~200 rows; sequential scan is cheap. SKIP.
- `assets.asset_type` — `AssetRepository.findByAssetTypeOrderBySymbolAsc`. Asset master is ≤ ~200 rows; sequential scan is cheap. SKIP.

## Final V47 index list (5 indexes)

| # | Index name | Table.columns | Predicate | Motivation | Selectivity (single-user) | Write cost |
|---|------------|---------------|-----------|------------|---------------------------|------------|
| 1 | `idx_holdings_asset_id` | `portfolio_holdings(asset_id)` | none | `HoldingRepository.findByPortfolioIdAndAssetId` second predicate; the `UNIQUE(portfolio_id, asset_id)` index leads with `portfolio_id` so `asset_id`-only lookups fall back to seq scan | ~1-2 rows per asset_id at single-user scale | ~16 bytes per row; portfolio_holdings is mutated O(N transactions) so write amplification is sub-millisecond |
| 2 | `idx_price_alerts_asset_id` | `price_alerts(asset_id)` | none | `ON DELETE CASCADE` reverse scan when `assets.id` row is removed; V9 indexes `(user_id)` + `(status)` but never `asset_id` | ~1-3 alerts per asset at single-user scale | ~16 bytes per row; price_alerts is rarely written |
| 3 | `idx_alert_notifications_alert_id` | `alert_notifications(alert_id)` | none | `ON DELETE CASCADE` reverse scan when a `price_alerts.id` row is removed; V9 indexes `(user_id, created_at DESC)` + `(user_id) WHERE read_at IS NULL` but never `alert_id` standalone (V10 dropped the NOT NULL on alert_id but it remains FK) | ~1-N notifications per alert | ~16 bytes per row; notifications grow append-only |
| 4 | `idx_alert_notifications_asset_id` | `alert_notifications(asset_id)` | `WHERE asset_id IS NOT NULL` | `ON DELETE CASCADE` reverse scan when `assets.id` is removed; V10 dropped the NOT NULL so partial keeps the index tight | ~1-N notifications per asset | ~16 bytes per row; partial restricts to populated rows |
| 5 | `idx_savings_goals_linked_portfolio_id` | `savings_goals(linked_portfolio_id)` | `WHERE linked_portfolio_id IS NOT NULL` | `ON DELETE SET NULL` reverse scan when a `portfolios.id` is removed; column is nullable + unindexed today | ~few linked goals at single-user scale | ~16 bytes per row; partial keeps the index tight |

Five indexes, all motivated, all under the plan's hard cap of 10. Bills.due_day, assets.asset_type, recurring_templates.category_id, allocation_buckets.category_id, transaction_category_rules.category_id all SKIPPED with documented reasons (small table, soft FK, or fully covered by an existing leading-column index). authenticators.credential_id is NOT a foreign key (it is a `BYTEA UNIQUE`) and the unique constraint already produces a covering index.
