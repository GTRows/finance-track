-- Cash flow allocator (Phase 10.7). A user saves an ordered list of buckets
-- (e.g. "Savings 30%", "Investments 20%", "Cash buffer 10%"); the preview
-- endpoint takes an income amount plus an obligations amount and returns how
-- much to assign to each bucket out of the discretionary leftover.
CREATE TABLE allocation_buckets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    percent     NUMERIC(5, 2) NOT NULL,
    category_id UUID,
    ordinal     INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT allocation_buckets_percent_ck CHECK (percent >= 0 AND percent <= 100)
);

CREATE INDEX idx_allocation_buckets_user ON allocation_buckets(user_id, ordinal);
