# CLAUDE.md — Self-hosted application contract

Drop this file at the **root of the application repository**. It tells
Claude Code what it is responsible for, and just as importantly, what it
is **not** responsible for.

## 0. Scope of this file

This project is an **application**, packaged as one or more Docker
containers. Operators (the upstream maintainer, forks, third parties)
deploy it into their own infrastructure. The application repo's job ends
at the image registry; everything beyond that — reverse proxy, TLS, DNS,
authentication, backups, alerting, host firewalling — is the operator's
choice.

Claude's responsibility in this repo:

- Build the application: source code, tests, Dockerfile, Compose file,
  CI pipeline, release automation.
- Make the application **portable**: configurable through env vars,
  parametric on every host-specific value, with sensible defaults that
  work on a fresh laptop running `docker compose up`.
- Document the contract operators rely on: ports, volumes, env vars,
  health endpoints, log format, backup-friendly data layout.

What Claude does **not** do here:

- Pick a reverse proxy, a DNS provider, an identity provider, an
  alerting channel, or a backup tool. Those are operator decisions.
- Hard-code maintainer-specific hostnames, IPs, or credentials.
- Add a sidecar that only makes sense in one operator's environment
  (e.g. a Cloudflare Tunnel client, a Tailscale daemon, a litestream
  config tied to a specific R2 bucket).

If a request requires one of those, push it back to the operator-level
deployment repo, not into this application.

---

## 1. What this app is

Fill in for every project:

> One paragraph: what the app does, primary tech stack, single- or
> multi-tenant, expected scale (single user, household, small team).

## 2. Repository layout (mandatory)

```
<app-root>/
  README.md                  # see section 11
  CLAUDE.md                  # this file
  docker-compose.yml         # smallest working stack
  docker-compose.override.yml.example   # optional local-dev tweaks
  .env.example               # every env var the app reads, dummy values
  .gitignore                 # must list .env, ./data, ./.cache, etc.
  .dockerignore
  Dockerfile                 # multi-stage; produces the published image
  config/                    # static, version-controlled config (mounted :ro)
  data/                      # bind-mount target. Gitignored. Created on first run.
  scripts/                   # smoke-test, db-shell, migrate, restore-test
  src/                       # application source
  CHANGELOG.md               # user-visible changes per release
  docs/
    MIGRATION.md             # one entry per minor version: ops steps + rollback
```

`docker-compose.yml` is the canonical artifact. It must produce a
working instance against the project's own defaults — no operator-specific
prerequisites.

## 3. Docker conventions

### 3.1 Image references

- Pin every external image to a specific version tag
  (`postgres:16-alpine`, not `postgres:latest`).
- Multi-arch (`linux/amd64` + `linux/arm64`) for the application's own
  images. Operators may run on x86 servers or ARM single-board computers.
- Do not pin a digest of an upstream image inside the repo's compose file
  unless there is a documented reason — operators handle digest pinning
  in their deployment fork.

### 3.2 Port bindings — parametric, never `0.0.0.0`

Compose:

```yaml
ports:
  - "${BIND_ADDR:-127.0.0.1}:${HTTP_PORT:-8080}:8080"
```

`.env.example`:

```
# Interface to publish the HTTP listener on.
# 127.0.0.1  - local only (default, safe)
# 10.0.0.5   - LAN interface
# 100.x.y.z  - VPN interface (Tailscale / Nebula / WireGuard)
BIND_ADDR=127.0.0.1
HTTP_PORT=8080
```

Never bake a real IP into the compose file. Never default to `0.0.0.0`.
The operator picks the bind interface; the application defaults to
loopback so a fresh `docker compose up` is safe.

### 3.3 Networks

Every stack declares a named network:

```yaml
networks:
  myapp-net:
    name: myapp-net
```

Never rely on the default bridge.

### 3.4 Volumes — bind mounts under `./data/`

```yaml
volumes:
  - ./data/postgres/:/var/lib/postgresql/data
  - ./data/uploads/:/app/uploads
  - ./config/migrations/:/app/migrations:ro
```

- All persistent state under `./data/<component>/`. Operators target this
  path with whatever backup tool they choose.
- Static config under `./config/` is version-controlled and read-only.
- Named Docker volumes are forbidden for stateful data — they are
  invisible to backup tools and to humans inspecting disk usage.

If a previous version used named volumes, document the migration step
in `docs/MIGRATION.md` (export from the named volume, copy into
`./data/`, remove the volume).

### 3.5 Healthchecks — required for every stateful service

Every container something else `depends_on` declares a `healthcheck`.
Dependents use `condition: service_healthy`. The check must reflect real
readiness (DB reachable, migrations applied), not just process liveness.

### 3.6 Restart policy

`restart: unless-stopped`. Not `always`, not `no`.

### 3.7 Resource limits

Declare CPU and memory limits on every container. Document the defaults
in the `README.md` so operators with smaller hosts can tune them.

### 3.8 No Docker socket exposure

The application never mounts `/var/run/docker.sock`.

## 4. Configuration and secrets

### 4.1 The `.env` rule

- All runtime configuration is read from environment variables.
- `.env` is gitignored.
- `.env.example` is committed and documents every variable, with safe
  placeholders and a one-line comment per variable (purpose, format,
  whether it is a secret).
- Never log a value sourced from `.env`.

### 4.2 Secret generation

Claude does not generate real secrets. When a new secret is needed,
output the generation command for the operator and reference the
variable in `.env.example` with a placeholder
(`CHANGE_ME_BASE64_60`) plus a comment naming the command:

```
# openssl rand -base64 60
JWT_SECRET=CHANGE_ME_BASE64_60
```

### 4.3 What never goes in source

Real tokens, passwords, DB connection strings with credentials, private
keys, JWTs, API tokens. `gitleaks` runs in CI and as a pre-commit hook.
Bypassing with `--no-verify` is not allowed.

## 5. Database conventions

### 5.1 Choosing a database

Default to **SQLite** for single-writer workloads. Reach for **Postgres**
when concurrent writers, full-text search, JSONB, or multi-process
workers demand it. Never introduce a database the project does not
already use without documenting why.

### 5.2 Backup-friendly data layout

The application is responsible for **putting data where backups can find
it**. It is not responsible for performing the backup.

- All persistent data under `./data/<component>/`.
- For Postgres: also write a periodic `pg_dump --format=custom` into
  `./data/backups/<date>.dump` from a sidecar, **only if the operator
  enables it via env**:

  ```
  ENABLE_PG_DUMP=true
  PG_DUMP_SCHEDULE=0 3 * * *
  PG_DUMP_RETENTION_DAYS=7
  ```

  Default off. The operator decides whether they want app-level dumps,
  host-level snapshots, or an external streamer.
- For SQLite: do not bundle litestream inside this image. Ship the DB
  file at a stable path and let the operator add a streamer sidecar in
  their deployment fork.

### 5.3 Migrations

- Every schema change is a versioned, reversible migration in
  `config/migrations/`.
- Auto-run on container start **only if** the migration tool is
  idempotent and tested for re-entry. Otherwise migrations are an
  explicit script in `scripts/migrate.sh` documented in
  `docs/MIGRATION.md`.
- Never edit a migration after it has run anywhere. Add a new one.

### 5.4 What you must never do

- Sync a live DB file via Syncthing / Dropbox / iCloud / `rsync`.
- Copy `*.sqlite3` from a running container with `cp`.
- Run two writers against the same DB.

## 6. The application's place in the request path

The application **does not terminate TLS** and **does not run its own
reverse proxy** in production. It listens on plain HTTP on the bound
interface; the operator places whatever they want in front (Caddy,
Traefik, nginx, Cloudflare Tunnel, Tailscale Serve).

### 6.1 Forwarded headers

Trust `X-Forwarded-For`, `X-Forwarded-Proto`, and `X-Forwarded-Host`
only when the source address is in the configured trusted-proxy list.
Frameworks expose a `trusted_proxies` setting — make it configurable
via env (`TRUSTED_PROXIES=10.0.0.0/8,100.64.0.0/10`).

### 6.2 Canonical URL

`PUBLIC_URL` is authoritative. The app must:

- emit absolute URLs (email links, OAuth callbacks, image refs) using
  exactly that origin;
- set the cookie domain to that hostname (or a parent of it);
- match WebAuthn relying-party ID to that hostname.

A mismatch silently breaks WebAuthn, OAuth, and cookie scoping.

## 7. Authentication

The application supports two modes, switched by env:

### 7.1 Built-in auth (default)

The app handles login and (optionally) registration. Use a maintained
library; never hand-roll JWT validation. Suitable for solo operators
and quick local trials.

### 7.2 Reverse-proxy forward-auth (opt-in)

When the operator runs an SSO gateway (Authentik, Authelia, Keycloak,
oauth2-proxy) in front, the app trusts identity headers injected by the
proxy:

```
AUTH_MODE=forward-auth
AUTH_HEADER_USER=X-Forwarded-User
AUTH_HEADER_EMAIL=X-Forwarded-Email
AUTH_HEADER_GROUPS=X-Forwarded-Groups
TRUSTED_PROXIES=10.0.0.0/8,100.64.0.0/10
```

The headers are trusted **only** when the request source IP matches
`TRUSTED_PROXIES`. This is the single safety mechanism that prevents
header injection from external clients; it is not optional.

The app does **not** ship an OIDC client, an OAuth flow, or a session
backed by an external identity provider. That layer lives in the
operator's reverse proxy / SSO gateway, not inside this image.

### 7.3 Sessions

Use Redis (Postgres apps) or a dedicated SQLite file (SQLite apps).
Sessions are not backed up — losing them just logs people out.

## 8. Logging

Containers write **structured JSON to stdout / stderr**. The application
does not write log files. The application does not maintain its own
rotation policy. The operator picks the destination by configuring the
Docker logging driver.

### 8.1 Required fields

`ts` (RFC3339), `level` (debug/info/warn/error), `msg`, `service`. Add
`request_id` when in scope of an HTTP request, `user_id` when known.

### 8.2 Content rules

- **Never log secrets.** Deny-list filter for `password`, `token`,
  `secret`, `authorization`, `cookie`, `set_cookie`, `api_key`,
  `client_secret`, `private_key`. Replace with `<redacted>`.
- **Never log full request bodies.** Log path, method, response code,
  user ID, elapsed time.
- Default level `info`. `debug` is off in production.

### 8.3 Correlation

Generate a request ID at the application edge or accept `X-Request-Id`
from the proxy. Echo it on every log line in scope of that request.

## 9. Metrics and health

### 9.1 Prometheus

`GET /metrics` on the main listener (or a separate metrics port bound to
loopback). At minimum: HTTP request count + duration histogram,
`process_*`, one or two domain counters. Restrict by interface binding,
not by application auth.

### 9.2 Health endpoints

- `GET /healthz` — 200 only when the app can serve traffic (DB
  reachable, migrations applied, dependencies up).
- `GET /livez` — 200 when the process is alive.

A health endpoint that always returns 200 silently breaks orchestration.

**Stateless frontends** (Next.js / SPA / static SSR with no DB or
downstream dependency they own) are an exception: their `/healthz` and
`/livez` collapse to the same check — process is up and able to answer
HTTP. Do **not** make a frontend `/healthz` ping the backend; the
operator's reverse proxy health-checks each upstream independently, and
mixing them turns a backend outage into a frontend outage in the proxy's
view, hiding the real failure and breaking partial-availability UX.

## 10. Updates

### 10.1 Versioning

Semantic versioning. `vX.Y.Z` pushed as a Git tag triggers a CI release:
multi-arch image build, push to the registry, GitHub release with the
matching `CHANGELOG.md` entry.

### 10.2 What the application repo publishes

For every release, **without exception**:

- A new `vX.Y.Z` tag in the registry. **No `:latest`**, no floating
  alias.
- A `CHANGELOG.md` section listing user-visible changes.
- A `docs/MIGRATION.md` section listing operator-visible changes:
  required env vars added or removed, schema migrations introduced,
  one-shot commands the operator must run, and the rollback recipe.
  Empty section is fine for releases that need no migration steps —
  write `No migration steps.` explicitly.

### 10.3 Breaking changes

Major-version bumps signal breaking changes. Do not hide a breaking
change behind a minor or patch bump. The release notes name the
incompatibility explicitly.

### 10.4 What you must never do

- `image: foo/bar:latest` — banned (3.1).
- Bundle Watchtower, Renovate config aimed at operators, or any
  auto-update daemon. Updates are an operator concern.
- Land an in-place schema change without a migration file.

## 11. README.md sections (every project)

Required sections, in this order:

1. **One-paragraph description.**
2. **Quick start** — smallest copy-pasteable `docker compose up` recipe
   producing a working local instance against the project's defaults.
3. **Configuration** — full env var table sourced from `.env.example`
   with the comments preserved.
4. **Exposure** — recommended reverse-proxy snippets (Caddy, Traefik,
   nginx) and TLS guidance, written as **examples for operators**, not
   as required defaults.
5. **Data and backup hooks** — what's in `./data/`, recommended RPO,
   what is **not** stateful and can be discarded.
6. **Updating** — link to `CHANGELOG.md` and `docs/MIGRATION.md`, with
   the rollback recipe summarized.

`docs/MIGRATION.md` carries per-version operator steps. `CHANGELOG.md`
carries user-visible release notes.

## 12. CI gates (mandatory)

`.github/workflows/ci.yml` runs on every push and PR:

- Application lint + test.
- `gitleaks-action` against the staged diff and full history.
- `trivy fs .` for filesystem CVEs and `trivy image <built-tag>` for the
  built image.
- `docker compose -f docker-compose.yml config` to catch syntax errors
  and unresolved env vars.
- Optional: `hadolint Dockerfile`, `actionlint`, `shellcheck` on
  `scripts/`.

A merge to `main` is blocked on all of these passing.

## 13. What Claude must NOT do

- Hard-code an IP, hostname, domain, identity provider, email provider,
  registry path, or backup destination into the application or its
  defaults. Every host-specific value goes through env.
- Default to `0.0.0.0` or `"PORT:PORT"` port bindings.
- Add `:latest`, Watchtower, or any auto-update daemon.
- Ship a sidecar that pins the application to one operator's stack
  (Cloudflare Tunnel client, Tailscale daemon, restic config with a
  specific repository, litestream config with a specific bucket).
- Generate or paste a real secret.
- Sync a live DB file via file-level tools.
- Bypass `gitleaks` with `--no-verify`.
- Add a dependency on a service the project does not already use without
  documenting it in the same PR.

## 14. When in doubt

1. Ask: "Is this a property of the application, or of one specific
   deployment?" If it's the second, it does not belong in this repo.
2. Make the value an env var with a safe default.
3. Document the env var in `.env.example` and the README.
4. Stop. The operator (or the operator's deployment repo) takes it from
   there.
