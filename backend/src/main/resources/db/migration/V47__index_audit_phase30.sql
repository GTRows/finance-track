-- Phase 30 sub-plan 02 / Track F2. Missing-index audit on FK columns
-- whose parent-delete reverse scan or single-column lookup falls back to
-- a sequential scan today. Each index below names the query path it
-- serves, the assumed selectivity, and the write-cost note. CREATE INDEX
-- (not CONCURRENTLY) is safe at single-user homelab scale because every
-- target table is < ~10k rows and the migration's transactional lock
-- is sub-second. See `.planning/phases/30-performance-and-polish/30-02-PLAN.md`
-- for the full inspection notes (FK column inventory, leading-column
-- coverage decisions, and the indexes intentionally SKIPPED).

-- (1) portfolio_holdings.asset_id
-- Motivation: HoldingRepository.findByPortfolioIdAndAssetId second predicate.
-- The V1 UNIQUE(portfolio_id, asset_id) index leads with portfolio_id, so
-- asset_id-only equality lookups fall back to a sequential scan today.
-- Selectivity: ~1-2 holdings per asset_id at single-user scale; the new
-- index turns the seq scan into a single-row index hit.
-- Write cost: ~16 bytes per row; portfolio_holdings is mutated O(N
-- transactions) so the per-write amplification is sub-millisecond.
CREATE INDEX idx_holdings_asset_id ON portfolio_holdings(asset_id);

-- (2) price_alerts.asset_id
-- Motivation: ON DELETE CASCADE reverse scan when an assets.id row is
-- removed; V9 indexes (user_id) and (status) but never asset_id alone.
-- Selectivity: ~1-3 active alerts per asset at single-user scale; the
-- partial-vs-full asset deletion path needs an index to avoid a seq scan
-- of the full alerts table per parent delete.
-- Write cost: ~16 bytes per row; price_alerts is rarely written.
CREATE INDEX idx_price_alerts_asset_id ON price_alerts(asset_id);

-- (3) alert_notifications.alert_id
-- Motivation: ON DELETE CASCADE reverse scan when a price_alerts.id row
-- is removed; V9 indexes (user_id, created_at DESC) and the partial
-- (user_id) WHERE read_at IS NULL but never alert_id standalone.
-- Selectivity: ~1-N notifications per alert; deleting an alert needs a
-- direct lookup of children, not a seq scan of the notifications table.
-- Write cost: ~16 bytes per row; notifications grow append-only with
-- price-tick frequency, so the index trails closely behind the heap.
CREATE INDEX idx_alert_notifications_alert_id ON alert_notifications(alert_id);

-- (4) alert_notifications.asset_id (partial)
-- Motivation: ON DELETE CASCADE reverse scan when an assets.id row is
-- removed; V10 dropped the NOT NULL on alert_notifications.asset_id (so a
-- BUDGET_RULE-source notification carries asset_id IS NULL), so a partial
-- index excluding NULLs keeps the index byte-tight.
-- Selectivity: notifications with a populated asset_id form the majority
-- of historical rows; the partial trims the BUDGET_RULE-source rows that
-- never carry an asset reference.
-- Write cost: ~16 bytes per non-NULL row.
CREATE INDEX idx_alert_notifications_asset_id ON alert_notifications(asset_id)
    WHERE asset_id IS NOT NULL;

-- (5) savings_goals.linked_portfolio_id (partial)
-- Motivation: ON DELETE SET NULL reverse scan when a portfolios.id row is
-- removed; column is nullable and unindexed today. Most goals are
-- unlinked, so a partial WHERE linked_portfolio_id IS NOT NULL covers
-- the live FK rows without indexing every NULL.
-- Selectivity: a handful of linked goals at single-user scale; the
-- partial index is small and only the linked rows pay the maintenance
-- cost on writes.
-- Write cost: ~16 bytes per linked row.
CREATE INDEX idx_savings_goals_linked_portfolio_id ON savings_goals(linked_portfolio_id)
    WHERE linked_portfolio_id IS NOT NULL;
