CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID,
    username VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_log_action ON audit_log (action, created_at DESC);
