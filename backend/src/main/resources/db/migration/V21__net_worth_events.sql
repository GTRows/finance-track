CREATE TABLE net_worth_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_date DATE NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    label VARCHAR(120) NOT NULL,
    note TEXT,
    impact_try NUMERIC(20, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_net_worth_events_user_date ON net_worth_events (user_id, event_date DESC);
