CREATE TABLE savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    target_amount NUMERIC(20, 2) NOT NULL CHECK (target_amount > 0),
    target_date DATE,
    linked_portfolio_id UUID REFERENCES portfolios(id) ON DELETE SET NULL,
    notes TEXT,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_goals_user_active ON savings_goals (user_id) WHERE archived_at IS NULL;

CREATE TABLE savings_goal_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    contribution_date DATE NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_contributions_goal_date ON savings_goal_contributions (goal_id, contribution_date DESC);
