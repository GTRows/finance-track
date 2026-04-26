# Quick Start

This guide takes a brand-new operator from a fresh deployment to a
working dashboard with one portfolio, one bill, and one budget entry.
It assumes the stack is already running per `docs/DEV_SETUP.md` or the
`docker compose up -d` flow in the README.

Total time: ~5 minutes.

## 1. Create the first user

Open the app at `http://localhost:5173` (dev) or your deployed URL.
The owner registers themselves on first run (there is no admin
provisioning step):

1. On the login screen, click **Create account** at the bottom.
2. Pick a username (>= 3 characters), enter your email, and choose a
   password (>= 8 characters; the strength meter is advisory).
3. Submit. You're signed in immediately and land on the empty
   dashboard.

The first user becomes the owner. Subsequent registrations work but
this app is designed for single-user use.

## 2. Optional: enable two-factor (TOTP)

Strongly recommended if the instance is exposed to the internet.

1. Open **Settings -> Security**.
2. Click **Enable Two-Factor Authentication**.
3. Scan the QR code with an authenticator app (Aegis, Authy, 1Password,
   Google Authenticator).
4. Type the 6-digit code to confirm.
5. **Save the recovery codes shown on screen** — they are displayed
   exactly once. They get you back in if you lose the authenticator.

Future logins ask for the 6-digit code after username + password.

## 3. Set your locale

1. **Settings -> General**.
2. Pick your **currency**, **language**, **theme**, and **timezone**.
3. Changes apply immediately; no logout needed.

The app stores values in TRY internally regardless of display
currency, so changing currency only affects formatting.

## 4. Add your first portfolio

1. Open **Portfolios** in the sidebar.
2. Click **+ Add portfolio**.
3. Fill in:
   - **Name**: e.g. "Main account" or "BES (pension)".
   - **Type**: pick the icon that matches (individual / pension / crypto / etc.).
   - **Description** (optional).
4. Submit.

The portfolio appears in the list with zero holdings.

## 5. Add a holding

1. Click into the portfolio you just created.
2. Click **+ Add holding**.
3. Search for an asset by ticker or name (BTC, AKBNK, gold, etc.). The
   asset master list ships with the most common Turkish funds, BIST
   stocks, top crypto, FX, and metals.
4. Enter the quantity and your unit cost (in TRY).
5. Submit. The holding shows up with a current value pulled from the
   live price.

If the asset you want isn't there, **Prices -> + Add fund** lets you
import a TEFAS fund by code; for stocks, the import flow is similar
under the same screen.

## 6. Record a transaction

1. From the portfolio detail page, scroll to **Transactions** and
   click **+ Record transaction**.
2. Pick **BUY** or **SELL**, the asset, the date, the quantity, and
   the unit price.
3. Submit. The holding is recomputed (cost basis + average price)
   automatically.

This is the canonical way to grow or shrink a position over time
rather than editing the holding directly.

## 7. Add a recurring bill

1. Open **Bills** in the sidebar.
2. Click **+ Add bill**.
3. Fill in:
   - **Name**: e.g. "Electricity" or "Spotify".
   - **Amount** in TRY.
   - **Due day of month** (1-31).
   - **Auto-renew** toggle if it should keep recurring.
4. Submit.

The bill appears in the calendar. Click **Pay** when you've actually
paid it; the payment is logged and used to compute the next due date.

## 8. Log income/expenses

1. Open **Budget** in the sidebar.
2. Pick the month you want to log into (current month by default).
3. Click **+ Add transaction** at the top of the table.
4. Toggle **INCOME** or **EXPENSE**, type an amount, optionally pick a
   category and tags, set the date.
5. Submit.

The summary cards at the top of the page roll up income, expense, and
savings rate as you add rows. Categories are managed via the **Manage
categories** button on the same page.

## 9. Take a backup

Habit-forming step:

```bash
./scripts/backup.sh
```

For repeated automated backups, install the systemd timer per
`docs/OPERATIONS.md`. Per-user JSON exports are also available from
**Settings -> Backup**.

## What's next

- **Dashboard**: at-a-glance net worth, portfolio breakdown, monthly
  income/expense, upcoming bills.
- **Analytics**: cash-flow projection, benchmarks, savings-rate trend.
- **Capital gains report**: per-year P&L for tax filing.
- **Push notifications**: enable from **Settings -> Notifications** so
  alerts and bill reminders reach you on browsers that support web
  push.
- **Alerts**: from the **Alerts** page, set price alerts on watched
  assets — the backend evaluates them against the live price feed.

For deeper module-level docs, see `docs/ARCHITECTURE.md`,
`docs/SECURITY.md`, and `docs/API.md`. For day-2 operations, see
`docs/OPERATIONS.md`.
