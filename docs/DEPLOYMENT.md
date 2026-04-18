# Self-Hosted Deployment

Single user, self-hosted on the owner's own machine. Domain: `fatihaciroglu.dev`.

The reference deployment target is **this Windows 11 host** with Docker Desktop.
A Linux path is documented at the bottom for completeness.

## Ingress modes

FinTrack ships with two ingress paths; pick one before running `up`:

- **Traefik (default).** The compose stack declares Traefik router labels on
  `frontend` and `backend`. Layer `docker-compose.traefik.yml` on top to attach
  both services to the external Traefik network; the bundled Nginx + Certbot
  stay off. This is how FinTrack integrates with the homelab stack.
- **Bundled Nginx (fallback).** When no Traefik is available, start Nginx +
  Certbot explicitly with `--profile nginx`. Traefik labels remain but do
  nothing without the external network; TLS is provisioned on-box via
  `scripts/ssl-setup.sh`.

Traefik mode:

```powershell
# Ensure the Traefik network exists (usually provisioned by the Traefik stack).
docker network create traefik-proxy   # no-op if it already exists
docker compose -f docker-compose.yml -f docker-compose.traefik.yml up -d --build
```

Bundled Nginx fallback:

```powershell
docker compose --profile nginx up -d --build
```

Tune the Traefik hostname, entrypoint, and cert resolver via `TRAEFIK_HOST`,
`TRAEFIK_ENTRYPOINT`, `TRAEFIK_CERT_RESOLVER`, and `TRAEFIK_NETWORK` in `.env`
(defaults: `fatihaciroglu.dev`, `websecure`, `letsencrypt`, `traefik-proxy`).

## 1. Prerequisites

- Docker Desktop for Windows, running, with "Start Docker Desktop when you log in" enabled
- WSL2 backend enabled in Docker Desktop
- A static (or at least stable) public IP, or a Dynamic DNS client
- Router port-forward: external 80 -> host 80, external 443 -> host 443
- Windows Defender Firewall: allow inbound on TCP 80 and 443
- DNS at the registrar:
  - `fatihaciroglu.dev`       A  -> public IP
  - `www.fatihaciroglu.dev`   A  -> public IP
- Git for Windows (for `bash` + `curl` + `jq` if you want the shell smoke test)

## 2. First-time install (Windows / PowerShell)

```powershell
# Clone somewhere stable. Avoid OneDrive-synced paths.
git clone <this-repo> D:\fintrack
cd D:\fintrack

# Copy env template, then edit .env and fill in secrets.
Copy-Item .env.example .env
notepad .env

# Build + start everything (Traefik ingress; homelab default).
docker network create traefik-proxy   # no-op if it already exists
docker compose -f docker-compose.yml -f docker-compose.traefik.yml up -d --build

# Or: bundled Nginx fallback if no external Traefik is available.
# docker compose --profile nginx up -d --build
```

The compose stack includes a built-in `backup` container that takes a daily
`pg_dump` of the database into `./backups/` with 30-day retention. No Windows
Task Scheduler setup needed. Tune via `BACKUP_INTERVAL_SECONDS` and
`BACKUP_RETENTION_DAYS` in `.env`.

Verify:

```powershell
Invoke-RestMethod http://localhost/api/v1/health
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

## 3. Issue the Let's Encrypt certificate

Only needed when running the bundled Nginx fallback. With external Traefik,
TLS is managed by the homelab-side cert resolver referenced by
`TRAEFIK_CERT_RESOLVER`.

DNS and port-forwarding must already be live.

```powershell
# Uses Git Bash (ships with Git for Windows) so the shell script runs natively.
bash scripts/ssl-setup.sh

# Optional: dry-run against the LE staging environment first.
$env:STAGING = "1"; bash scripts/ssl-setup.sh; Remove-Item Env:STAGING
```

The `certbot` service in the compose stack auto-renews every 12h via webroot,
so you do not need to re-run `ssl-setup.sh` after the initial issuance.

## 4. Auto-start on boot

Docker Desktop's "Start at login" setting brings the engine up when Windows
signs the owner in; every service in `docker-compose.yml` has `restart: always`,
so the whole stack comes back automatically. No extra wiring needed.

For "headless" start (before any user logs in) switch Docker Desktop to "Run
Docker Desktop as a service" (Settings -> General) if the installed edition
supports it, or convert to WSL2's `dockerd` under systemd.

## 5. Backups + restore

- Daily dumps in `./backups/` via the `backup` compose service (see logs with
  `docker compose logs -f backup`)
- Restore: `bash scripts/restore.sh backups/fintrack_YYYYMMDD_HHMMSS.sql.gz`

## 6. Upgrades

```powershell
cd D:\fintrack
git pull
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

Flyway applies pending migrations on backend start.

## 7. Monitoring (Prometheus + Grafana)

The compose stack includes a metrics stack wired to the backend's
`/api/actuator/prometheus` endpoint.

- **Prometheus** runs internally (no published ports) and scrapes the backend every 30s.
  Config at `monitoring/prometheus.yml`, retention 30 days.
- **Grafana** is published on the host at `http://localhost:${GRAFANA_PORT}`
  (default 3001). Login with `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from
  `.env`.
- The "FinTrack Overview" dashboard is provisioned automatically from
  `monitoring/grafana/dashboards/fintrack-overview.json` — CPU, heap, live threads,
  Hikari connections, HTTP request rate / P95 latency / 4xx-5xx rate, JVM memory.
- A second dashboard, "FinTrack Business"
  (`monitoring/grafana/dashboards/fintrack-business.json`), is provisioned from
  the custom business metrics the backend exposes: portfolio total value,
  current-month income / expense, computed savings rate, transactions today,
  and alerts fired per source (`price`, `budget`). Metric names are prefixed
  `fintrack_` so they sit in their own namespace in Prometheus. Gauges refresh
  every 60 seconds (see `BusinessMetrics`).

To keep Grafana private, leave `GRAFANA_PORT` bound to localhost in Docker Desktop
(default behaviour) and don't add an Nginx route for it.

## 8. Logs & rotation

Backend logs live in the `app-logs` Docker volume (mounted at `/var/log/fintrack`
inside the container). The production profile uses a size-and-time rolling
policy: files rotate daily and again once they exceed `LOG_MAX_FILE_SIZE`, with
gzipped archives named `fintrack.YYYY-MM-DD.N.log.gz`. Oldest archives are
deleted first once the combined on-disk size passes `LOG_TOTAL_SIZE_CAP`, so
disk usage stays bounded regardless of traffic.

Tune the caps in `.env` — these are the defaults:

| Variable | Default | Meaning |
|---|---|---|
| `LOG_MAX_FILE_SIZE` | `50MB` | Max size of the live file before forced rollover |
| `LOG_MAX_HISTORY_DAYS` | `90` | Days of archives to keep |
| `LOG_TOTAL_SIZE_CAP` | `1GB` | Combined cap across `fintrack.*.log.gz` |
| `LOG_ERROR_TOTAL_SIZE_CAP` | `256MB` | Combined cap for the ERROR-only archive |

Values accept `KB` / `MB` / `GB` suffixes. Example: to give a larger disk 5 GB
of headroom, set `LOG_TOTAL_SIZE_CAP=5GB` and restart the backend container.

To inspect archives from the host:

```powershell
docker compose exec backend ls -lh /var/log/fintrack
docker compose exec backend zcat /var/log/fintrack/fintrack.2026-04-17.0.log.gz | tail -n 200
```

## 9. Troubleshooting

```powershell
docker compose ps
docker compose logs -f backend
docker compose logs -f nginx
docker compose logs -f certbot
docker compose logs -f backup
docker compose exec postgres psql -U fintrack fintrack
```

Common issues:
- 502 from nginx right after `up`: backend is still starting (healthcheck
  `start_period` is 60s). Wait ~1 minute.
- Certbot fails with "connection refused": DNS is not yet pointing at the
  public IP, or port 80 is not actually forwarded to this host.
- WebSocket does not connect behind HTTPS: check `wss://fatihaciroglu.dev/ws`
  in DevTools. Nginx already proxies `/ws/` with Upgrade/Connection headers.

## 10. Log shipping to Loki (optional)

If your homelab runs Loki + Grafana for centralised logs, enable the bundled
Promtail sidecar. It auto-discovers containers via the Docker socket and only
scrapes ones labelled `com.fintrack.log=true` (only the backend by default).

1. In `.env`, set `LOKI_URL` to the Loki push endpoint
   (e.g. `http://loki.homelab.lan:3100`).
2. Start with the `loki` profile:

   ```powershell
   docker compose --profile loki up -d promtail
   ```

Labels sent to Loki: `app=fintrack`, `container=fintrack-api`,
`service=backend`, plus the log `level` parsed from the JSON console output
(see `logback-spring.xml`). Standard queries:

```logql
{app="fintrack", level="ERROR"}
{app="fintrack"} |= "userId=\"abc123\""
```

The Promtail config lives at `monitoring/promtail.yml`. It reads the `timestamp`,
`level`, `logger`, `msg`, `userId`, and `requestId` fields from each JSON log
line; everything else stays in the log body.

## 11. CrowdSec integration (optional)

If your homelab runs CrowdSec, FinTrack emits a stable audit line for every
auth-adjacent event (`LOGIN`, `TOTP_VERIFY`, `PASSWORD_CHANGE`, etc.). Ready
snippets live in `docs/crowdsec/`:

- `fintrack-audit.yaml` -- parser that extracts `action`, `status`, `username`,
  `source_ip`, and `detail` from each line.
- `fintrack-bruteforce.yaml` -- scenario that bans a source IP after 5 failed
  login / 2FA attempts within 5 minutes.

Install on the CrowdSec host:

```bash
sudo cp docs/crowdsec/fintrack-audit.yaml /etc/crowdsec/parsers/s01-parse/
sudo cp docs/crowdsec/fintrack-bruteforce.yaml /etc/crowdsec/scenarios/
sudo systemctl reload crowdsec
```

Then point `acquis.yaml` at the backend log file (when shipping logs from the
docker volume) or at the journald unit if you're collecting container logs
upstream.

## 12. Wazuh integration (optional)

If your SIEM is Wazuh (OSSEC-derived), FinTrack is compatible out of the box:
the structured `AUDIT` log line in `fintrack.log` is parseable with the
decoder + rules in `docs/wazuh/`.

1. Install the Wazuh agent on the Docker host.
2. Point `ossec.conf` at the backend log file. On Windows (default deployment):

   ```xml
   <localfile>
     <location>C:\ProgramData\Docker\volumes\fintrack_app-logs\_data\fintrack.log</location>
     <log_format>syslog</log_format>
   </localfile>
   ```

   On Linux: `/var/lib/docker/volumes/fintrack_app-logs/_data/fintrack.log`.

3. Copy decoder + rules to the Wazuh manager:

   ```bash
   sudo cp docs/wazuh/fintrack-decoder.xml /var/ossec/etc/decoders/
   sudo cp docs/wazuh/fintrack-rules.xml /var/ossec/etc/rules/
   sudo systemctl restart wazuh-manager
   ```

Supported action field values are defined in
`backend/src/main/java/com/fintrack/audit/AuditAction.java` -- add a rule line
for any new action you want to alert on (the generic rules above already cover
success / failure patterns).

## 13. Homarr dashboard tile

If you run Homarr as the homelab launcher, FinTrack drops in as a standard app
tile. The public-facing URL is `https://fatihaciroglu.dev` and the health
endpoint `/api/v1/health` is unauthenticated (see `SecurityConfig` public paths)
and returns `{"status":"UP", ...}` -- Homarr's ping integration treats any 2xx
response as online.

| Field | Value |
|---|---|
| Name | FinTrack |
| Internal address | `http://backend:8080` (from the Homarr container, if on the same compose network) |
| External address | `https://fatihaciroglu.dev` |
| Ping URL | `https://fatihaciroglu.dev/api/v1/health` |
| Icon | `/icons/fintrack.svg` (copy `frontend/public/icon.svg` into Homarr's icons volume) |

Add-as-JSON snippet (Homarr v1 "Add an app" -> Import):

```json
{
  "name": "FinTrack",
  "url": "https://fatihaciroglu.dev",
  "behaviour": { "openInNewTab": true },
  "network": {
    "enabledStatusChecker": true,
    "statusCodes": ["200"],
    "statusUrl": "https://fatihaciroglu.dev/api/v1/health"
  },
  "appearance": {
    "iconUrl": "/icons/fintrack.svg"
  }
}
```

If Homarr runs on the same Docker network as FinTrack, point the status checker
at `http://backend:8080/api/v1/health` instead to avoid bouncing off the public
hostname and Nginx.

## Linux host (alternative)

If you ever move off Windows, the repo also ships:

- `scripts/fintrack.service` -- systemd unit to run `docker compose up` at boot
- `scripts/fintrack-backup.service` + `.timer` -- optional replacement for the
  in-compose backup loop, if you prefer systemd to own the schedule

Install:

```bash
sudo cp scripts/fintrack*.service scripts/fintrack*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now fintrack.service
# Optional -- only if you disable the 'backup' compose service:
sudo systemctl enable --now fintrack-backup.timer
```
