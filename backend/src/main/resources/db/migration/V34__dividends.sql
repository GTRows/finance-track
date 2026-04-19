-- Dividend ledger. Dividends sit alongside investment transactions but carry
-- tax-relevant fields the main ledger does not (per-share amount, withholding
-- tax, ex-dividend date). Amounts are stored in the payment currency plus a
-- snapshot converted to TRY at payment time so historical reports stay stable
-- even if exchange rates change later.

CREATE TABLE dividends (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id      UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id          UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    amount_per_share  NUMERIC(20, 8),
    shares            NUMERIC(20, 8),
    gross_amount      NUMERIC(20, 4) NOT NULL,
    withholding_tax   NUMERIC(20, 4) NOT NULL DEFAULT 0,
    net_amount        NUMERIC(20, 4) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'TRY',
    net_amount_try    NUMERIC(20, 4) NOT NULL,
    payment_date      DATE NOT NULL,
    ex_dividend_date  DATE,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dividends_portfolio ON dividends(portfolio_id, payment_date DESC);
CREATE INDEX idx_dividends_asset ON dividends(asset_id, payment_date DESC);
