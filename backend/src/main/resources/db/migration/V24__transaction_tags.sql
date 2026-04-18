CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

CREATE INDEX idx_tags_user ON tags (user_id);

CREATE TABLE transaction_tags (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_transaction_tags_tag ON transaction_tags (tag_id);

-- Migrate existing text[] tags on transactions into the new tables.
INSERT INTO tags (user_id, name)
SELECT DISTINCT t.user_id, trim(unnest(t.tags))
FROM transactions t
WHERE t.tags IS NOT NULL AND array_length(t.tags, 1) > 0
ON CONFLICT (user_id, name) DO NOTHING;

INSERT INTO transaction_tags (transaction_id, tag_id)
SELECT t.id, tg.id
FROM transactions t
JOIN LATERAL unnest(t.tags) AS tag_name ON TRUE
JOIN tags tg ON tg.user_id = t.user_id AND tg.name = trim(tag_name)
WHERE t.tags IS NOT NULL AND array_length(t.tags, 1) > 0
ON CONFLICT DO NOTHING;

ALTER TABLE transactions DROP COLUMN tags;
