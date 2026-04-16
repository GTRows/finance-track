# Security Model

## JWT Strategy

Two-token system: short-lived access token + long-lived refresh token.

```
Access Token:  15 minutes, in-memory (Zustand store only — never localStorage)
Refresh Token: 30 days, stored in:
               - Server: PostgreSQL refresh_tokens table (can be revoked)
               - Client: httpOnly cookie (XSS-proof) OR Zustand (simpler for initial impl)
```

**Token rotation:** Every time a refresh token is used, it is deleted and a new pair is issued.
This means a leaked refresh token can only be used once before it's invalidated.

### Auth Flow

```
1. Login
   POST /api/auth/login
   → Backend verifies password (BCrypt)
   → Creates access token (JWT, 15min, signed with HS256)
   → Creates refresh token (JWT, 30 days, saved to DB)
   → Returns both to frontend

2. API Requests
   Every request: Authorization: Bearer <accessToken>
   JwtAuthFilter reads token, validates signature + expiry
   Sets SecurityContext with user details

3. Token Refresh (automatic)
   Frontend Axios interceptor catches 401 response
   Calls POST /api/auth/refresh with refreshToken
   Backend: validates refresh token exists in DB and not expired
   Backend: deletes old refresh token, creates new pair
   Frontend: updates stored tokens, retries original request

4. Logout
   POST /api/auth/logout with refreshToken
   Backend deletes refresh token from DB
   Frontend clears tokens from memory/cookie
   Future requests get 401, user redirected to login

5. Forced logout (security event)
   Admin or self: DELETE all refresh_tokens WHERE user_id = X
   All sessions invalidated immediately
```

## Password Security

- BCrypt with strength 12 (deliberately slow: ~300ms per hash)
- Minimum 8 characters enforced at API level
- No password stored in plain text anywhere

## Rate Limiting

Two layers: Nginx (IP-based) + Redis (application-level)

```
Nginx limits:
  /api/auth/* → 5 req/min per IP (login brute force protection)
  /api/*      → 30 req/min per IP
  /*          → 60 req/min per IP

Application limits (Redis counters, for fine-grained control):
  Login attempts: 10 per IP per 15 minutes → account lockout warning
```

## CORS

Configured in `SecurityConfig.java`:
- Development: allow all origins (`*`)
- Production: restrict to your domain only

Change in `application.yml`:
```yaml
cors:
  allowed-origins: "https://yourdomain.com"  # production
```

## Security Headers (Nginx)

```
X-Frame-Options: SAMEORIGIN              # no iframing
X-Content-Type-Options: nosniff          # no MIME sniffing
X-XSS-Protection: 1; mode=block         # legacy XSS filter
Referrer-Policy: strict-origin-when-cross-origin
Content-Security-Policy: default-src 'self'; ...
Strict-Transport-Security: max-age=63072000  # HTTPS only (production)
```

## External Access (Production)

To safely expose to the internet:

1. **Domain:** Point DNS A record to your server IP
2. **SSL:** Run `scripts/ssl-setup.sh` — gets Let's Encrypt certificate
3. **Nginx:** Uncomment SSL server block in `nginx/nginx.conf`
4. **Firewall:** Only expose ports 80 and 443 (never 8080, 5432, 6379 directly)
5. **Strong secrets:** Generate JWT_SECRET with `openssl rand -base64 64`
6. **Strong passwords:** POSTGRES_PASSWORD and REDIS_PASSWORD should be 32+ chars

## Two-Factor Authentication (TOTP)

Optional, per-user. Compatible with any RFC 6238 authenticator (Google Authenticator,
Authy, 1Password, Bitwarden, etc.).

**Enrollment (Settings → Security):**
1. `POST /api/v1/auth/2fa/setup` — backend generates a 160-bit Base32 secret, stores
   it unenabled, returns the secret plus an `otpauth://` URI. Frontend renders the URI
   as a QR code.
2. User scans QR, types the 6-digit code back, frontend calls `POST /api/v1/auth/2fa/enable`.
   Backend verifies the code against the stored secret and flips `users.totp_enabled = true`.

**Login flow when TOTP is enabled:**
1. User submits password. Backend recognises `totp_enabled` and, instead of issuing an
   access token, returns a short-lived (5 min) JWT challenge token with claim
   `type=TOTP_CHALLENGE`.
2. Frontend renders a 6-digit code prompt, then calls `POST /api/v1/auth/2fa/verify`
   with `{ challengeToken, code }`. Backend validates the challenge claim + the TOTP
   code (±1 step drift) and returns the real access/refresh pair.

**Disabling:** `POST /api/v1/auth/2fa/disable` requires the user's password again; the
stored secret is wiped.

**Secret handling:** Secrets live in `users.totp_secret` (VARCHAR 64). They never leave
the backend after enrollment except during the one-time provisioning response.

## What's NOT in Scope (yet)

- OAuth2 (Google/GitHub login) — future
- IP allowlist — can be added in Nginx if needed
- Audit logging (who changed what) — future
- End-to-end encryption of DB fields — not needed for single-user

## Secrets Management

Never commit `.env` to git. `.env` is in `.gitignore`.
Use `.env.example` as template.

For production, secrets can be passed as:
- Docker Compose env file (current approach)
- Docker secrets (future: for Swarm/K8s)
- AWS Secrets Manager (if migrating to AWS)

Required secrets (all in `.env`):
```
JWT_SECRET          — min 256 bits (32 chars), random
POSTGRES_PASSWORD   — strong random password
REDIS_PASSWORD      — strong random password
```
