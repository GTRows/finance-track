# Threat Model

STRIDE analysis covering the authentication and data layers of FinTrack
Pro. Aimed at the single-operator self-hosted deployment, with optional
external HTTPS exposure.

For implementation details of the controls referenced below, see
`docs/SECURITY.md`.

## Trust Boundaries

```
Internet
   |  (HTTPS via Nginx)
   v
+--------------------+   +--------------------+
| Frontend container |   |  Operator on host  |
|   (Vite/Nginx)     |   |  (root, docker)    |
+--------------------+   +--------------------+
   |  REST/JSON, JWT bearer        |  shell, env, files
   v                                v
+----------------------------------------------+
| Backend container (Spring Boot, Java 21)     |
|  - JWT mint + verify                          |
|  - JPA over PostgreSQL                        |
|  - STOMP WebSocket fan-out                    |
|  - Outbound HTTP to TEFAS/CoinGecko/Yahoo     |
+----------------------------------------------+
   |  JDBC                          |  Redis protocol
   v                                v
+--------------------+   +--------------------+
| PostgreSQL 16      |   | Redis 7            |
| (volume on host)   |   | (cache, sessions)  |
+--------------------+   +--------------------+
```

Trust boundaries (each row is a thing to defend across):

1. Internet -> Nginx (TLS termination, rate limit, security headers).
2. Nginx -> Backend (HTTP loopback inside the docker network).
3. Backend -> Postgres (network segment + DB credentials in env).
4. Backend -> Redis (network segment, no auth in default deploy).
5. Backend -> External price APIs (outbound only, public APIs).
6. Operator -> Host (the operator is trusted; if compromised, all
   bets are off).

## In scope

- The Spring Boot REST + WebSocket surface area.
- The auth flows (login, register, refresh, TOTP, password reset,
  email verification, recovery codes).
- The per-user data store (everything keyed by `user_id` in Postgres).
- The browser-side token storage and the React Query cache.

## Out of scope

- Threats requiring root on the host (already total compromise).
- Supply-chain attacks against npm/Maven (mitigated by lockfiles +
  the Spotless/lint pipeline; not modeled here).
- Physical access to the host.
- Insider threat: this is a single-operator app.

## STRIDE - Authentication layer

### Spoofing

| Threat | Control |
|--------|---------|
| Stealing valid credentials and impersonating the user | BCrypt-hashed passwords; rate-limited login (`LoginRateLimiter`); optional TOTP. |
| Replaying a captured access token | Short TTL (15 min); HTTPS in production; tokens never in URL. |
| Replaying a captured refresh token | Refresh tokens are single-use: the row is deleted on use and a new one inserted (rotation), see `D10` in roadmap. |
| Forging a JWT | Tokens signed with HS256 and a server-side secret loaded from `.env`; rejected if signature is invalid. |
| Stealing a TOTP code | Codes are 30s windowed; `LoginRateLimiter.enforceSensitive` caps verification attempts per IP. |

### Tampering

| Threat | Control |
|--------|---------|
| Modifying a JWT to escalate role | HS256 signature verification rejects modified payloads. |
| Modifying password reset/email-verify token in URL | Tokens are random 32-byte values, stored hashed; comparison is constant-time. |
| Tampering with stored auth records via app | All writes go through the service layer with explicit user-id checks; no direct repository exposure to the controller. |

### Repudiation

| Threat | Control |
|--------|---------|
| User denies they made a security-sensitive action | `AuditService` logs `LOGIN`, `LOGIN_FAILURE`, `TOTP_ENABLED`, `TOTP_DISABLED`, `TOTP_RECOVERY_REGENERATED`, `TOTP_RECOVERY_REDEEMED`, `PASSWORD_CHANGED`, `MAIL_TEST` etc. with timestamp + IP + user agent. |
| Admin denies running an admin action | Admin endpoints under `/api/v1/admin/*` are role-gated and audited where they mutate state. |

### Information disclosure

| Threat | Control |
|--------|---------|
| Email enumeration via password-reset endpoint | `/auth/password-reset/request` always returns 200 regardless of whether the email exists. |
| Username enumeration via login error | Login returns the same generic error for "wrong username" and "wrong password". |
| Leaking refresh tokens via XSS | `Content-Security-Policy` + the React app never injects user content as raw HTML; refresh token is in localStorage which is XSS-reachable, mitigated by tight CSP. (See "Known residual risk" below.) |
| Leaking JWT secret | Loaded from `.env`; never logged; not committed (`.env` ignored). |

### Denial of service

| Threat | Control |
|--------|---------|
| Brute-force login attempts | `LoginRateLimiter` blocks per-IP after N failures; lockout window is configurable. |
| Spamming password-reset / email-verify endpoints | `enforceSensitive("password-reset")`, `enforceSensitive("email-verify-send")` per-IP counters. |
| Refresh token spray | Single-use rotation invalidates the token regardless; failed validations counted separately. |
| TOTP code guessing | 6-digit code with `enforceSensitive` cap and a rolling window. |

### Elevation of privilege

| Threat | Control |
|--------|---------|
| Regular user calling admin endpoints | `SecurityConfig` enforces `hasRole("ADMIN")` on `/api/v1/admin/**`; the request goes through `JwtAuthFilter` which seeds the `SecurityContext` with the persisted role. |
| Mass-assignment of role via registration | Registration DTO accepts only `username/email/password`; the role is hard-coded `USER` in `AuthService.register`. |
| Disabling TOTP without a password | `AuthService.totpDisable` requires the current password (BCrypt-checked). |

## STRIDE - Data layer

### Spoofing

| Threat | Control |
|--------|---------|
| One user reading another user's portfolio/transactions | Every repository query for owned data is filtered by `userId` (the JWT principal); service-layer assertions throw `ResourceNotFoundException` if the row exists but doesn't belong to the caller. |
| Forged WebSocket subscription on someone else's `/topic/prices` | Prices are global (asset-level), not per-user; the topic doesn't carry per-user data. |

### Tampering

| Threat | Control |
|--------|---------|
| Mass-assignment via REST request body adding `userId` | DTOs are Jakarta records with the explicit set of fields; `userId` is taken from the JWT, not the body. |
| SQL injection via input | All queries are JPA criteria or `@Query` bound parameters; no string concatenation. |
| Modifying audit log to hide actions | The audit log table is append-only from app code; no `UPDATE` or `DELETE` paths are exposed. |
| Tampering with backup file before import | The import flow restores under a single `@Transactional`; if any constraint fails, the entire restore rolls back. The user must trust the file they upload (it's their own export). |

### Repudiation

| Threat | Control |
|--------|---------|
| User denies they deleted a portfolio / transaction | `created_at` / `updated_at` timestamps on every entity; audit log captures security-sensitive ops. Domain ops are not currently audited (residual risk; see roadmap D7). |

### Information disclosure

| Threat | Control |
|--------|---------|
| API leaking another user's data | Every controller takes the principal via `@AuthenticationPrincipal`; service queries are `userId`-scoped; integration-tested via WebMvcTest suites. |
| Backup file containing another user's data | `BackupService.export(userId)` only selects rows where `user_id = ?`; the payload schema has no global tables. |
| Receipt files served without auth | `ReceiptStorageService.load(userId, txnId)` checks ownership before reading the file; `/api/v1/budget/transactions/{id}/receipt` requires auth. |
| Logs leaking PII | Default log format excludes request/response bodies; password / token fields are never logged. |

### Denial of service

| Threat | Control |
|--------|---------|
| Huge backup payload locking the DB | Restore is `@Transactional`; PostgreSQL holds the lock briefly. No streaming parser today (residual risk for very large payloads). |
| Filling the receipts directory | `ReceiptStorageService` enforces a max file size and MIME-type allow-list. |
| Endless price refresh hammering external APIs | `PriceSyncService` runs on a fixed schedule with internal backoff; manual refresh endpoint is auth-gated. |

### Elevation of privilege

| Threat | Control |
|--------|---------|
| Read-only user mutating data | All endpoints other than auth/health require `ROLE_USER` (or `ROLE_ADMIN`); there is no read-only role today. |
| Backup-import overwriting a different user's rows | `BackupService.restore(userId, payload)` only writes rows under `userId`; the payload has no cross-user references because the export already filtered to that user. |

## Known residual risk

- **Refresh token in localStorage**: XSS would let an attacker exfiltrate
  it. Mitigations: strict CSP, the React bundle ships no third-party
  widgets, lint forbids `eval`, and the app does not inject user
  content as raw HTML. Moving to httpOnly cookies would close this but
  breaks the current SPA flow on cross-origin deployments.
- **Domain audit gap**: only auth + admin actions are logged. Adding
  audit rows for portfolio/transaction/budget mutations is roadmap
  D7.
- **No WAF in front of Nginx**: rate limiting + body-size caps live in
  nginx.conf; OWASP-grade WAF is out of scope for the home-lab setup.
- **Backups stored on the same host as Postgres**: a destroyed host
  loses both the live DB and the backups. Off-host copy is documented
  in `docs/OPERATIONS.md` and left to the operator.
- **Email-verify and password-reset tokens are single-use but not
  bound to a session**: an attacker with both the email content and
  the user's password could chain attacks; TOTP defeats this.

## When to update this document

- Anytime a new endpoint changes the auth model (new role, new
  challenge step, new public path).
- Anytime a new persistent secret is added (new key, new external
  API token).
- Anytime a new entity is added to the data layer - make sure the
  per-user filter assumption still holds.
