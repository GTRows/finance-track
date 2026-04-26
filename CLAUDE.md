# FinTrack Pro — Project Brain

Personal finance & investment tracking system. Self-hosted, open-source, production-ready.
Single user (owner) with optional external HTTPS access. Runs on Docker, starts on boot.

## What This Project Is

A full-stack web app replacing an Excel spreadsheet for tracking:
- Investment portfolios (bireysel + BES/pension funds) with live prices
- Monthly income & expenses with log history
- Recurring bills with payment tracking and reminders
- Real-time dashboard with charts and P&L

The owner already has an Excel file (`Yatirim_Takip_final_v2.xlsx`) with existing data.
Import from that file is a future milestone.

## Detailed Docs (read before working on a module)

| What | File |
|------|------|
| Full architecture & tech decisions | `docs/ARCHITECTURE.md` |
| Database schema & entity relationships | `docs/DATABASE.md` |
| API contracts (all endpoints) | `docs/API.md` |
| Frontend structure & component patterns | `docs/FRONTEND.md` |
| Security model (JWT, auth flow) | `docs/SECURITY.md` |
| External price APIs & integration | `docs/PRICE_APIS.md` |
| Dev setup & common commands | `docs/DEV_SETUP.md` |
| Operations runbook (backup, restore, key rotation, migrations) | `docs/OPERATIONS.md` |
| Current progress & what's next | `tasks/TODO.md` |
| Lessons learned (update after mistakes) | `tasks/LESSONS.md` |

## Tech Stack

**Backend:** Java 21, Spring Boot 3.2, Spring Security 6, Spring Data JPA, Flyway
**Frontend:** React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui, Recharts, Zustand, React Query
**Database:** PostgreSQL 16 (Flyway migrations), Redis 7 (cache + sessions)
**Infra:** Docker Compose, Nginx (reverse proxy + SSL), systemd (auto-start)
**Auth:** JWT with access (15min) + refresh (30 days) token rotation

## Project Structure

```
fintrack/
├── backend/                  # Spring Boot — Java 21
│   └── src/main/java/com/fintrack/
│       ├── auth/             # JWT auth, register, login, refresh
│       ├── portfolio/        # Holdings, transactions, snapshots
│       ├── budget/           # Income, expenses, monthly summaries
│       ├── bills/            # Recurring bills, payment history
│       ├── price/            # Price sync schedulers + API clients
│       ├── notification/     # Email + in-app alerts
│       ├── report/           # PDF/CSV export
│       ├── ai/               # Claude API integration (optional)
│       ├── websocket/        # STOMP WebSocket for live prices
│       └── common/           # Entities, DTOs, exceptions, config
├── frontend/                 # React + Vite
│   └── src/
│       ├── pages/            # Route-level views
│       ├── components/       # Feature-based: layout/, dashboard/, portfolio/, budget/, bills/, charts/, ui/
│       ├── api/              # Axios client + per-feature API modules
│       ├── store/            # Zustand state slices
│       ├── hooks/            # React Query hooks + custom hooks
│       ├── types/            # TypeScript interfaces
│       └── utils/            # Formatters, calculators
├── nginx/                    # nginx.conf
├── scripts/                  # setup.sh, backup.sh, ssl-setup.sh, fintrack.service
├── docs/                     # Detailed specs (read before coding)
├── tasks/                    # TODO.md, LESSONS.md
├── docker-compose.yml
├── .env.example
└── CLAUDE.md                 # ← you are here
```

## Key Commands

```bash
# Start everything
docker compose up -d

# Backend only (dev)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=development

# Frontend only (dev)
cd frontend && npm run dev

# Run backend tests
cd backend && ./mvnw test

# Run frontend tests
cd frontend && npm run test

# Check logs
docker compose logs -f backend
docker compose logs -f frontend

# DB migration status
cd backend && ./mvnw flyway:info

# Rebuild after code change
docker compose up -d --build backend
```

## Coding Conventions

**Java/Spring:**
- Feature-based packages: each feature is self-contained (controller + service + repo + dto in same package)
- DTOs are Java records (immutable)
- All endpoints return `ResponseEntity<T>` with explicit status codes
- Validation via Jakarta Bean Validation (`@Valid`, `@NotBlank`, etc.)
- Service layer owns all business logic — controllers are thin
- Use `@Transactional` on service methods that write to DB
- No hardcoded strings — use constants or config values

**TypeScript/React:**
- Functional components only, no class components
- Custom hooks for all data fetching (`usePortfolio`, `useBudget`, etc.)
- React Query for server state, Zustand for client state
- All API calls go through `src/api/client.ts` (axios with interceptors)
- `shadcn/ui` for all UI primitives — don't reinvent buttons, inputs, dialogs
- `Recharts` for all charts — wrap in `src/components/charts/`
- Format currency with `formatTRY()` from `src/utils/formatters.ts`
- TypeScript strict mode — no `any`, no `@ts-ignore`

**General:**
- Never commit `.env` files — use `.env.example` as template
- Every new DB change = new Flyway migration file (`V{n}__description.sql`)
- New backend feature = new package under `com.fintrack.{feature}/`
- New frontend feature = new folder under `src/components/{feature}/`

## Core Principles

- **Simplicity first:** minimal code, if you can delete lines instead of adding, do that
- **No temporary fixes:** find root causes
- **Only touch what's necessary:** minimal blast radius per change
- **Verify before done:** run tests, check logs, confirm it works
