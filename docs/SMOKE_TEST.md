# Smoke Test

Two passes: automated (API) first, then manual (UI). Run both before declaring a release ready.

## Prerequisites

- Docker Desktop running
- `.env` created from `.env.example` with real secrets
- Ports 80 / 443 / 5432 / 6379 / 8080 available

## A. Automated (API)

Windows (host):

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -BaseUrl http://localhost:8080
```

Linux / macOS / Git Bash:

```bash
docker compose up -d --build
./scripts/smoke-test.sh
./scripts/smoke-test.sh http://localhost:8080
```

The script exits non-zero on the first failing check. Passing means:
- `/api/v1/health` is OK within 90s
- register / login / `/auth/me`
- `GET` + `PUT /settings` (currency round-trip)
- create portfolio, list assets, add holding
- `GET /dashboard`
- logout

## B. Manual (UI)

Open `http://localhost/` (or `https://fatihaciroglu.dev/` in production).

### Auth
- [ ] Register screen creates a new user and redirects to dashboard
- [ ] Logout + log back in with the same credentials works
- [ ] Language switcher toggles TR / EN instantly across all pages

### Settings
- [ ] Change currency to USD: dashboard KPIs and holdings re-render with USD
- [ ] Change theme (Light / Dark / System): colors flip, persists after reload
- [ ] Change language via Settings (not topbar): reloads in the new language

### Portfolio
- [ ] Create a portfolio (try each of the 8 types)
- [ ] Add a BTC holding; prices populate within ~30s (check via live ticker)
- [ ] Record a BUY transaction: holding quantity & avg cost update automatically
- [ ] Record a SELL that empties the holding: holding disappears
- [ ] Allocation chart renders; history chart renders after first snapshot
- [ ] Download PDF report from portfolio detail page

### Budget
- [ ] Create income + expense transactions; category picker works
- [ ] Monthly KPIs (income / expense / net / savings rate) match the rows
- [ ] Capture monthly snapshot; row appears in Monthly Log section
- [ ] Download CSV export

### Bills
- [ ] Create a recurring bill with a due day in the next 3 days
- [ ] Pay it; status flips to paid and history row is created
- [ ] Calendar view shows the due-day marker in the correct color

### Dashboard / Live prices
- [ ] Live price ticker shows symbols and updates arrows (up/down) periodically
- [ ] Net worth, upcoming bills list, portfolio cards all populate

### Production-only
- [ ] `https://fatihaciroglu.dev/` loads with a valid Let's Encrypt cert
- [ ] HTTP redirects to HTTPS
- [ ] WebSocket connects (browser devtools: `wss://.../ws`)
- [ ] `docker compose logs certbot` shows the renew loop running
