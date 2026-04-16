-- Add session metadata so the user can see and revoke individual refresh tokens.

ALTER TABLE refresh_tokens
    ADD COLUMN user_agent VARCHAR(512),
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN last_used_at TIMESTAMPTZ;

UPDATE refresh_tokens SET last_used_at = created_at;
