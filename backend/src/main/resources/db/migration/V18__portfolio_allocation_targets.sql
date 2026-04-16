CREATE TABLE portfolio_allocation_targets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_type VARCHAR(30) NOT NULL,
    target_percent NUMERIC(6, 2) NOT NULL CHECK (target_percent >= 0 AND target_percent <= 100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (portfolio_id, asset_type)
);

CREATE INDEX idx_allocation_targets_portfolio ON portfolio_allocation_targets(portfolio_id);
