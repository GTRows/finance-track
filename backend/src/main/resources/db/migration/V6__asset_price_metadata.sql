-- ============================================================
-- V6: Attach external price source identifiers to seeded assets
-- so the sync workers know which remote id to query.
-- ============================================================

UPDATE assets
SET metadata = jsonb_build_object('coingeckoId', 'bitcoin')
WHERE symbol = 'BTC' AND asset_type = 'CRYPTO';

UPDATE assets
SET metadata = jsonb_build_object('coingeckoId', 'ethereum')
WHERE symbol = 'ETH' AND asset_type = 'CRYPTO';

UPDATE assets
SET metadata = jsonb_build_object('exchangeCode', 'USD')
WHERE symbol = 'USD' AND asset_type = 'CURRENCY';

UPDATE assets
SET metadata = jsonb_build_object('exchangeCode', 'EUR')
WHERE symbol = 'EUR' AND asset_type = 'CURRENCY';
