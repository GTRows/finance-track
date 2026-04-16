# Self-Hosted Deployment

Single user, self-hosted on the owner's own machine. Domain: `fatihaciroglu.dev`.

The reference deployment target is **this Windows 11 host** with Docker Desktop.
A Linux path is documented at the bottom for completeness.

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

# Build + start everything.
docker compose up -d --build
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

To keep Grafana private, leave `GRAFANA_PORT` bound to localhost in Docker Desktop
(default behaviour) and don't add an Nginx route for it.

## 8. Troubleshooting

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
