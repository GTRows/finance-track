-- ============================================================
-- V9: Price alerts and alert notifications.
-- Users configure thresholds on assets; when the price crosses
-- the threshold during a sync cycle, a notification row is
-- persisted and the alert moves to TRIGGERED.
-- ============================================================

CREATE TABLE price_alerts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id      UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    direction     VARCHAR(10) NOT NULL CHECK (direction IN ('ABOVE', 'BELOW')),
    threshold_try NUMERIC(20, 6) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'TRIGGERED', 'DISABLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    triggered_at  TIMESTAMPTZ
);
CREATE INDEX idx_price_alerts_user ON price_alerts(user_id);
CREATE INDEX idx_price_alerts_status ON price_alerts(status);

CREATE TABLE alert_notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id    UUID NOT NULL REFERENCES price_alerts(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id    UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    message     TEXT NOT NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alert_notifications_user_time ON alert_notifications(user_id, created_at DESC);
CREATE INDEX idx_alert_notifications_unread ON alert_notifications(user_id) WHERE read_at IS NULL;
