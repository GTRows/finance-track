CREATE TABLE authenticators (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  credential_id   BYTEA NOT NULL UNIQUE,
  public_key_cose BYTEA NOT NULL,
  sign_count      BIGINT NOT NULL DEFAULT 0,
  attestation_fmt VARCHAR(32),
  aaguid          UUID,
  transports      VARCHAR(64),
  name            VARCHAR(64) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at    TIMESTAMPTZ
);
CREATE INDEX idx_authenticators_user_id ON authenticators(user_id);
