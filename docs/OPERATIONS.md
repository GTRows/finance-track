# Operations Runbook

This document covers day-to-day operational procedures for a deployed
FinTrack Pro instance: backups, key rotation, schema migrations, and
recovery from a bad migration.

Audience: the single operator running the self-hosted deployment.

## Backups

### Manual backup

```bash
./scripts/backup.sh
```

Writes a gzipped `pg_dump` to `backups/fintrack_YYYYMMDD_HHMMSS.sql.gz`.
Files older than 30 days are pruned automatically.

The script requires the `postgres` container to be running.

### Scheduled backups (systemd timer)

```bash
sudo cp scripts/fintrack-backup.service /etc/systemd/system/
sudo cp scripts/fintrack-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now fintrack-backup.timer
sudo systemctl list-timers fintrack-backup.timer
```

The default timer runs daily. Edit the `OnCalendar=` line in the timer
file before enabling if a different cadence is needed.

### Off-host backup copy

The `backups/` directory lives on the same host as Postgres. For real
disaster recovery, ship copies elsewhere:

```bash
# Example: rsync to a remote box on each backup
rsync -az --delete backups/ user@offsite:/var/backups/fintrack/
```

Add this to a wrapper script and invoke it from the systemd service if
the operator wants this automated.

### Restore from backup

```bash
./scripts/restore.sh backups/fintrack_YYYYMMDD_HHMMSS.sql.gz
```

The script:

1. Stops the `backend` container so it releases connections.
2. `DROP DATABASE` and `CREATE DATABASE` (asks for `yes` confirmation).
3. Streams the gunzipped dump back through `psql`.
4. Restarts the backend.

Recovery time on a small dataset is seconds; large dataset depends on
the dump size. The frontend remains reachable but returns 5xx until the
backend restarts.

### Application-level export/import

For per-user export bundles (JSON), use the `/api/v1/backup/export` and
`/api/v1/backup/import` endpoints — see `docs/API.md`. These are the
right tool when migrating between hosts that have different schema
versions or different operator credentials.

## VAPID push keys

Web push notifications use a VAPID P-256 keypair. The backend reads
both halves from environment variables:

```bash
PUSH_VAPID_PUBLIC_KEY=<base64url, no padding>
PUSH_VAPID_PRIVATE_KEY=<base64url, no padding>
```

### First-run generation

If the env vars are missing or blank, `VapidKeyManager` generates a new
pair on startup and logs both halves at WARN level. Copy them into
`.env` and restart so future restarts reuse the same keypair —
otherwise existing browser subscriptions are silently invalidated.

### Rotating the keys

Rotation invalidates every existing browser subscription. Plan the
window with that in mind.

1. Generate a new pair (the easiest path is to wipe the env vars and
   restart the backend; copy the WARN-logged values from the logs).
2. Persist the new values in `.env`.
3. Restart the backend with the new pair set.
4. Truncate the stale subscriptions table so users are prompted to
   re-subscribe:
   ```sql
   TRUNCATE TABLE push_subscriptions;
   ```
5. Each user must re-enable push from Settings -> Notifications on
   every browser they used previously.

### Disabling push entirely

Leave the env vars set so the manager doesn't generate noise on
startup, then either truncate `push_subscriptions` or hide the
notification toggle in the UI. The manager logs only at startup.

## Schema migrations

Flyway runs every migration under `backend/src/main/resources/db/migration/`
on startup. The convention is `V{n}__short_description.sql`. Rules:

- Pick the next available `n` (use `./mvnw flyway:info` to confirm).
- Name the file in lower_snake_case after the next double underscore.
- Migrations are append-only; never edit a migration that has already
  been applied to a running database.
- Anything that changes data shape across hosts — including index
  changes — gets its own migration so it can be replayed deterministically
  on each environment.

### Inspecting state

```bash
cd backend
./mvnw flyway:info        # all known + applied migrations
./mvnw flyway:validate    # checks checksums match
```

In production:

```bash
docker compose exec backend java -jar app.jar -Dspring.profiles.active=production
# Flyway runs automatically on startup; check the log line "Successfully applied N migrations"
```

### Adding a new migration

1. Create `backend/src/main/resources/db/migration/V{n}__something.sql`.
2. Test locally:
   ```bash
   docker compose down -v       # wipe DB
   docker compose up -d postgres
   cd backend && ./mvnw spring-boot:run
   ```
3. Make sure the JPA entity matches the new schema.
4. Confirm the `FlywayMigrationTest` (Testcontainers) stays green.
5. Commit the migration and the entity change in the same PR.

## Recovering from a bad migration

If a migration partially applied and the backend now refuses to start
(Flyway `validate` failure, `checksum mismatch`, or the migration left
the schema in an inconsistent state):

1. **Stop the backend immediately** to prevent partial writes:
   ```bash
   docker compose stop backend
   ```
2. **Take a fresh dump** of the broken state — useful for forensics
   even if you ultimately restore from an earlier backup:
   ```bash
   ./scripts/backup.sh
   ```
3. **Restore the most recent good backup** taken before the migration
   was attempted:
   ```bash
   ./scripts/restore.sh backups/fintrack_<timestamp>.sql.gz
   ```
4. **Fix the offending migration file** in the repo. Either:
   - Edit it to be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`),
     and use `./mvnw flyway:repair` to clear the failed checksum row, or
   - Replace it with a corrected migration under the same `V{n}`.
5. Rebuild and restart the backend:
   ```bash
   docker compose up -d --build backend
   ```
6. Verify the `flyway_schema_history` table:
   ```sql
   SELECT version, description, success, installed_on
     FROM flyway_schema_history
     ORDER BY installed_rank DESC LIMIT 10;
   ```

If `flyway:repair` is needed in production, run it from inside the
backend container (the JAR ships with the Flyway CLI on the classpath).

### Testing a migration before production

Always:

1. Apply the migration against a copy of the production dump locally:
   ```bash
   ./scripts/restore.sh backups/<latest-prod-dump>.sql.gz
   docker compose up -d --build backend
   ```
2. Run the smoke test:
   ```bash
   ./scripts/smoke-test.sh
   ```
3. If the migration backfills data, time how long it takes on the prod-
   sized dataset before scheduling the production deploy.

## Health and observability

- `GET /api/v1/health` — overall + database + Redis component status.
- `GET /api/v1/health/system` — JVM heap, uptime, processor count.
- `GET /actuator/prometheus` — metrics endpoint (Spring Boot Actuator).
- Backend logs land in the directory configured by `logging.file.path`
  (default `/var/log/fintrack`); admins can tail them via the live SSE
  stream at `GET /api/v1/admin/logs/live`.

## Process recipes

- **Stop everything**: `docker compose down`
- **Rebuild backend after a code change**: `docker compose up -d --build backend`
- **Tail logs**: `docker compose logs -f backend frontend`
- **Open a psql shell**: `docker compose exec postgres psql -U fintrack`
- **Wipe and reseed local DB**: `docker compose down -v && docker compose up -d`
