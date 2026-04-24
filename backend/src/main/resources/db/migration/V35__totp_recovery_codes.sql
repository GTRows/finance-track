-- TOTP recovery codes for 2FA bypass during phone loss.
-- Each code is a single-use 10-char token hashed with BCrypt; 10 codes
-- are generated per user at TOTP enrollment and can be regenerated later.

CREATE TABLE totp_recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_totp_recovery_codes_user ON totp_recovery_codes(user_id);
CREATE INDEX idx_totp_recovery_codes_active ON totp_recovery_codes(user_id) WHERE consumed_at IS NULL;
