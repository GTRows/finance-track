-- GlitchTip database bootstrap. Mounted at
-- /docker-entrypoint-initdb.d/10-glitchtip.sql:ro on the existing FinTrack
-- postgres container. Postgres only runs init scripts on a fresh data volume,
-- so existing deployments must run this script manually (see docs/OPERATIONS.md
-- "GlitchTip / Sentry release tagging" -> "First-boot setup on an EXISTING
-- deployment"). The literal password below is a placeholder; the operator
-- rotates it via ALTER ROLE on first boot using GLITCHTIP_POSTGRES_PASSWORD
-- from their .env. Defence-in-depth only -- the role lives behind fintrack-net
-- with no host port published.

-- Idempotent: safe to run on fresh and existing volumes; uses DO blocks to
-- skip when the role / database already exists.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'glitchtip') THEN
    CREATE ROLE glitchtip WITH LOGIN PASSWORD 'glitchtip-change-me';
  END IF;
END
$$;

SELECT 'CREATE DATABASE glitchtip OWNER glitchtip'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'glitchtip')\gexec

GRANT ALL PRIVILEGES ON DATABASE glitchtip TO glitchtip;
