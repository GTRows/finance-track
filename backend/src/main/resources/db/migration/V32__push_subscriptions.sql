-- Web Push subscriptions (Phase 16.1). One row per browser/device the user
-- has granted notification permission from. The endpoint URL is issued by the
-- browser's push provider (FCM for Chrome, Mozilla autopush for Firefox, etc.)
-- and is globally unique, so we also use it as the upsert key. p256dh and
-- auth are required to send encrypted payloads later; for empty-body pushes
-- only the endpoint and VAPID signature are needed.
CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions(user_id);
