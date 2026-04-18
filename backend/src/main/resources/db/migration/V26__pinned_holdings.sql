ALTER TABLE portfolio_holdings
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_portfolio_holdings_pinned
    ON portfolio_holdings(portfolio_id)
    WHERE pinned = TRUE;
