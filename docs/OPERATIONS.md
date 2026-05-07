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

## GlitchTip / Sentry release tagging

Phase 26-02 stands up a self-hosted GlitchTip stack alongside the existing
FinTrack containers and wires the Spring Boot backend's Sentry SDK to it for
exception aggregation. Every captured event carries a `release` tag computed
from `IDENTITY.yaml`, the active Spring profile as `environment`, plus
`trace.id` / `span.id` / `request.id` tags so events cross-link to the Tempo
trace tree from 26-01.

The GlitchTip stack ships as a separate compose overlay at
`monitoring/glitchtip/docker-compose.glitchtip.yml`. The repository's main
`docker-compose.yml` is intentionally NOT modified by this phase (Claude
tooling is denied write access via the project's release-files guard). The
operator brings the stack up with the explicit overlay invocation shown
below.

### Required env vars

The following keys must be added to your existing `.env` file before bringing
the GlitchTip stack up. The repository's `.env.example` is intentionally NOT
updated by this phase (Claude tooling is denied write access to `.env.*`); use
this table as the canonical reference. Compose passes the project's `.env`
to every service automatically — Spring Boot reads `SENTRY_DSN` and
`FINTRACK_RELEASE_VERSION` directly from the backend container's environment
without any main-compose `environment:` block injection.

| Variable                        | Default (compose fallback)                       | Required in prod | Read by                                           |
| ------------------------------- | ------------------------------------------------ | ---------------- | ------------------------------------------------- |
| `SENTRY_DSN`                    | empty (SDK no-ops)                               | YES              | backend (`application.yml` `sentry.dsn`)          |
| `FINTRACK_RELEASE_VERSION`      | empty (falls back to `IDENTITY.yaml` version)    | no               | backend (`application.yml` `sentry.release`)      |
| `GLITCHTIP_POSTGRES_PASSWORD`   | `glitchtip-change-me` (placeholder; rotate)      | YES (rotate)     | overlay: `glitchtip-web`, `glitchtip-worker`      |
| `GLITCHTIP_SECRET_KEY`          | `change-me-32-bytes-of-random-data-please`       | YES (rotate)     | overlay: `glitchtip-web`, `glitchtip-worker`      |
| `GLITCHTIP_DOMAIN`              | `http://localhost:8000`                          | YES (set)        | overlay: `glitchtip-web`                          |
| `GLITCHTIP_EMAIL_URL`           | `consolemail://`                                 | no               | overlay: `glitchtip-web`, `glitchtip-worker`      |
| `GLITCHTIP_FROM_EMAIL`          | `noreply@fintrack.local`                         | no               | overlay: `glitchtip-web`, `glitchtip-worker`      |
| `GLITCHTIP_CELERY_AUTOSCALE`    | `1,3`                                            | no               | overlay: `glitchtip-web`, `glitchtip-worker`      |

Notes:
- `SENTRY_DSN` is a hard requirement under the production profile;
  `ProductionProfileGuard` aborts boot with a one-shot `IllegalStateException`
  listing all violations if it is blank in prod.
- `FINTRACK_RELEASE_VERSION` exists so a CI pipeline can stamp the release
  with the exact build's git short-sha (e.g. `1.1.0+abc1234`); leave blank
  for a clean tag derived from `IDENTITY.yaml`.
- `GLITCHTIP_SECRET_KEY` should be 32 bytes of random data; generate with
  `openssl rand -base64 32`.
- `GLITCHTIP_POSTGRES_PASSWORD` is the password for the dedicated `glitchtip`
  Postgres role created by `monitoring/glitchtip/init-db.sql`.

### Bringing the GlitchTip stack up

The GlitchTip services live in a compose overlay so the main `docker-compose.yml`
stays untouched. Always invoke compose with both files:

```bash
docker compose \
  -f docker-compose.yml \
  -f monitoring/glitchtip/docker-compose.glitchtip.yml \
  up -d
```

The overlay declares `fintrack-net` as `external: true`, so the main compose
must already be up (or come up in the same invocation as above) for the
network to exist. The overlay also merges a Postgres init-script mount onto
the existing `postgres` service — this is a no-op on existing data volumes
(the operator runs the SQL by hand per the next subsection).

### First-boot setup on an EXISTING deployment

Postgres only runs `docker-entrypoint-initdb.d/*.sql` against a fresh data
volume; existing FinTrack deployments must create the GlitchTip role and
database manually:

```bash
# 1. Add the env vars from the table above to your .env file.
# 2. Create the GlitchTip role + database in the existing Postgres container.
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres \
  < monitoring/glitchtip/init-db.sql

# 3. Rotate the placeholder password to your real value.
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres \
  -c "ALTER ROLE glitchtip WITH PASSWORD '<your GLITCHTIP_POSTGRES_PASSWORD>';"

# 4. Bring the GlitchTip stack up via the overlay.
docker compose \
  -f docker-compose.yml \
  -f monitoring/glitchtip/docker-compose.glitchtip.yml \
  up -d glitchtip-web glitchtip-worker

# 5. Wait for the Django app to become ready, then create an admin user.
docker compose \
  -f docker-compose.yml \
  -f monitoring/glitchtip/docker-compose.glitchtip.yml \
  exec glitchtip-web ./manage.py createsuperuser

# 6. In the GlitchTip web UI (route via Traefik or a temporary port-forward),
#    create a new project, copy the DSN, paste it as SENTRY_DSN in .env,
#    then restart the backend.
docker compose up -d --force-recreate backend
```

Fresh deployments (empty Postgres data volume) skip steps 2 and 3 — the
init script runs automatically on first container start when the overlay
is active.

### Verifying the wire-up

Provoke any handled error (e.g. an unauthenticated request against a
protected endpoint) and confirm in the GlitchTip web UI:
- one event arrives within ~10 seconds,
- `release` tag matches `fintrack@<IDENTITY.yaml version>` (or your
  `FINTRACK_RELEASE_VERSION` override),
- `environment` tag matches the active Spring profile,
- `trace.id` tag matches the Tempo trace ID for the same request,
- `request.id` tag matches the `X-Request-Id` response header,
- the rendered message is PII-scrubbed (no email, no IP, no JWT).

## SLI/SLO dashboard and burn-rate alerts

Phase 26-03 stands up the operator-facing SLO surface for FinTrack. Three
SLIs are graphed on a Grafana dashboard ("FinTrack SLO") and alerted on
via Prometheus rule files plus a self-hosted Alertmanager. The surface
observes server-side health: HTTP latency p95 (per route group), HTTP
error rate (5xx), and per-source price-sync freshness. It does NOT
observe fund freshness (TEFAS daily-tick model is information-only) or
client-side errors (out of scope at homelab footprint).

### SLOs

| SLI | SLO target | Window | Notes |
|---|---|---|---|
| HTTP latency p95 | 99% of requests under 500ms | 30 days | Per route group: `read`, `mutating`, `auth`. `prices` group exempted (refresh endpoints are structurally slow). |
| HTTP error rate | < 1% of requests return 5xx | 30 days | `prices` group excluded -- provider-side failures are not server-side. 4xx excluded -- client-side. |
| Price-sync freshness | All four live providers refresh within 6h | rolling | Per source: `crypto`, `currency`, `metal`, `stock`. Funds intentionally out of scope (TEFAS daily-tick model). |

### Bringing the SLO stack up

The Alertmanager service and the Prometheus rule mount ship as a compose
overlay so the main `docker-compose.yml` stays untouched (Claude tooling
is denied write access via the project's `pre_guard_release_files.py`
PreToolUse hook). Bring the full SLO stack up with the explicit overlay
invocation:

```bash
docker compose \
  -f docker-compose.yml \
  -f monitoring/prometheus/docker-compose.prometheus.yml \
  up -d
```

This adds `fintrack-alertmanager` (image `prom/alertmanager:v0.27.0`) on
the existing `fintrack-net` network and re-mounts the existing
`fintrack-prometheus` container with the rule file. Prometheus reads
`/etc/prometheus/alerts.yml` on startup; the active rules show up in the
Prometheus UI under Status -> Rules. The Grafana dashboard auto-loads
from the existing dashboards provisioner (no Grafana restart needed -- the
provisioning loop runs every 30 s).

### Tuning SLO targets

The 500 ms / 1% / 6 h numbers are baked into recording-rule expressions
and alert thresholds at `monitoring/prometheus/alerts.yml`. To change a
target, edit the relevant rule and reload Prometheus:

```bash
docker compose kill -s HUP fintrack-prometheus
```

Keep recording rules and alerts in sync -- the alert expression refers to
the recording rule by name, and the dashboard reads the recording series
on the same windowing.

### Wiring outbound notifications

The default `monitoring/prometheus/alertmanager.yml` ships with an empty
`default` receiver: alerts fire and aggregate on the Alertmanager UI but
no outbound routing is configured. To enable email notifications, edit
the receiver block to use the existing FinTrack SMTP env vars (already
documented in the mail / push section of this runbook). Sample SMTP
block:

```yaml
receivers:
  - name: 'default'
    email_configs:
      - to: 'ops@example.invalid'
        from: 'alerts@fintrack.local'
        smarthost: '${SMTP_HOST}:${SMTP_PORT}'
        auth_username: '${SMTP_USERNAME}'
        auth_password: '${SMTP_PASSWORD}'
        send_resolved: true
```

Webhook and Discord blocks follow the standard Alertmanager schema.
After editing, reload Alertmanager with `docker compose kill -s HUP
fintrack-alertmanager`.

### Burn-rate math reference

The two ratio-based SLIs (latency p95, error rate) use the Google SRE
workbook two-burn-rate envelope (chapter "Implementing SLOs"):

- **Fast (page)**: 1 h window x 14.4 burn rate. Fires when more than
  14.4x the error budget is being consumed over the last 1 h. With a 1%
  error budget, this corresponds to 2% burn over 1 h.
- **Slow (ticket)**: 6 h window x 6 burn rate. Fires when more than 6x
  the error budget is being consumed over the last 6 h, corresponding
  to 10% burn over 6 h.

Recording rules pre-compute the per-window fraction so the alert evaluator
and the dashboard read the same series. See
`monitoring/prometheus/alerts.yml` for the live definitions.

The freshness SLI uses a single-threshold-with-duration (6 h) per source.
Burn-rate math does not apply to a freshness SLI because the SLO is
binary (data is fresh / not fresh) rather than a fraction of bad
requests.

### Verifying after first boot

1. `docker compose -f docker-compose.yml -f monitoring/prometheus/docker-compose.prometheus.yml ps`
   shows `fintrack-prometheus` and `fintrack-alertmanager` as `Up (healthy)`.
2. `docker exec fintrack-prometheus wget -qO- localhost:9090/-/ready`
   returns HTTP 200 (or `curl` against the published port if the operator
   has published 9090).
3. Grafana -> "FinTrack SLO" dashboard loads with all six panels rendering.
   The dashboard uid is `fintrack-slo`.
4. After ~5 minutes of synthetic traffic, the error-rate panel reads a
   non-NaN value and the latency p95 panel shows one line per route group
   that has received traffic.
5. `ALERTS{alertstate="firing"}` queried from the Prometheus UI returns
   no rows when the SLOs are met.

### Operator footnote on locked release files

Three release-style files in this repository are deny-listed for Claude
tooling:

- `.env.example` -- denied by `Write(**/.env.*)` and `Edit(**/.env.*)`
  rules in `.claude/settings.json`.
- `docker-compose.yml` -- denied by the `pre_guard_release_files.py`
  PreToolUse hook.
- `CHANGELOG.md` -- denied by the same `pre_guard_release_files.py`
  hook.

Operator-facing config that would ordinarily live in `.env.example` or
`docker-compose.yml` is therefore routed through this `OPERATIONS.md`
document and through `monitoring/<feature>/docker-compose.<feature>.yml`
overlays (precedent: 26-02 GlitchTip; 26-03 Alertmanager / Prometheus
rules). `CHANGELOG.md` entries are written by an operator-side one-shot
splice. If any of these guards is relaxed in the future, the content
moves trivially in either direction; OPERATIONS.md stays the single
source of truth in either path.

## CI Security Gates

CI runs an opt-in OWASP Dependency Check job (`dependency-check` in
`.github/workflows/ci.yml`) that scans the Maven dependency tree for
known CVEs. The job is gated by `dorny/paths-filter@v3` so it only
executes when `backend/pom.xml` or `backend/owasp-suppressions.xml`
change, and it is informational only — it is not part of
`ci-complete`'s `needs[]`, so a CVE finding does not block unrelated
merges. The build fails only on CVSS >= 9.0 (CRITICAL) findings; HIGH
and MEDIUM are reported in the uploaded HTML/SARIF artefacts but do
not break the build.

### Obtaining an NVD API key

The plugin pulls the National Vulnerability Database feed. Without an
API key the public endpoint heavily rate-limits unauthenticated
requests and the job runtime balloons. Request a free key at
<https://nvd.nist.gov/developers/request-an-api-key>, then add it as a
GitHub Actions repository secret named `NVD_API_KEY`. The job exposes
it to Maven via the `NVD_API_KEY` env var which the `security` profile
consumes through `${env.NVD_API_KEY}` in `backend/pom.xml`.

The local opt-in run is:

```bash
cd backend && ./mvnw -B -ntp -P security org.owasp:dependency-check-maven:check
```

The first run downloads the full NVD feed and takes 3-5 minutes;
subsequent runs reuse the cached feed inside the 24-hour validity
window.

### Adding an OWASP suppression

Suppressions live in `backend/owasp-suppressions.xml` and silence a
specific CVE for a specific dependency. Use them sparingly — every
suppression is a security debt with a half-life. Each entry MUST
include a `<notes>` block stating why the suppression is acceptable
and a target review date so the entry is revisited rather than
becoming permanent.

Example:

```xml
<suppress until="2026-09-01">
  <notes><![CDATA[
    CVE-XXXX-NNNNN affects only the unused stream-parser code path in
    library X; we use the DOM API exclusively. Re-evaluate when the
    library publishes a fixed release.
  ]]></notes>
  <packageUrl regex="true">^pkg:maven/group/artifact@.*$</packageUrl>
  <cve>CVE-XXXX-NNNNN</cve>
</suppress>
```

Conventions:

- Always pin the suppression to the narrowest matcher possible
  (`<cve>` or `<vulnerabilityName>` rather than a blanket
  `<gav regex="true">.*</gav>`).
- Set `until="YYYY-MM-DD"` no more than 12 months out so the gate
  re-fires the finding when the date passes.
- The CI dependency-check job is informational; once a suppression
  is added, confirm the next CI run is clean before relying on the
  gate.

## Process recipes

- **Stop everything**: `docker compose down`
- **Rebuild backend after a code change**: `docker compose up -d --build backend`
- **Tail logs**: `docker compose logs -f backend frontend`
- **Open a psql shell**: `docker compose exec postgres psql -U fintrack`
- **Wipe and reseed local DB**: `docker compose down -v && docker compose up -d`
