# Technology Stack

**Analysis Date:** 2026-05-03

## Languages

**Primary:**
- Java 21 (LTS) — All backend application code (`backend/pom.xml`)
- TypeScript 5.4 (strict mode) — All frontend application code (`frontend/tsconfig.json`, `frontend/package.json`)

**Secondary:**
- SQL — Flyway migrations (`backend/src/main/resources/db/migration/V*.sql`)
- YAML — Spring config (`backend/src/main/resources/application.yml`), Docker Compose, GitHub Actions
- Bash — Operations scripts (`scripts/setup.sh`, `scripts/backup.sh`, `scripts/ssl-setup.sh`)

## Runtime

**Environment:**
- JDK 21 (Temurin OpenJDK in CI) — `backend/pom.xml` line `<java.version>21</java.version>`, `.github/workflows/ci.yml`
- Node.js 20.x (LTS) — `.github/workflows/ci.yml`
- Spring Boot 3.2.4 — `backend/pom.xml` `spring-boot-starter-parent`
- React 18.2.0 — `frontend/package.json`

**Package Managers:**
- Maven 3.x via `mvnw` wrapper — `backend/mvnw`, `backend/pom.xml`
- npm with `package-lock.json` — `frontend/package-lock.json`

## Frameworks

**Backend (core):**
- Spring Boot Starter Web 3.2.4 — REST endpoints
- Spring Security 6 — Authentication/authorization filter chain
- Spring Data JPA + Hibernate — ORM, repository abstractions
- Spring WebSocket (STOMP) — Live price broadcast (`backend/src/main/java/com/fintrack/websocket/`)
- Spring WebFlux WebClient — Non-blocking HTTP client for price APIs
- Spring Mail — SMTP notifications

**Frontend (core):**
- Vite 5.1 — Dev server, production bundler (`frontend/vite.config.ts`)
- React Router 6.22 — Client-side routing
- Tailwind CSS 3.4 — Utility-first styling (`frontend/tailwind.config.ts`)
- shadcn/ui via Radix primitives — UI components in `frontend/src/components/ui/`
- Recharts 2.12 — Charting (`frontend/src/components/charts/`)
- @tanstack/react-query 5.28 — Server-state cache
- Zustand 4.5 — Client UI state (`frontend/src/store/`)
- @stomp/stompjs 7.3 — WebSocket client

**Testing:**
- JUnit 5 (Jupiter) + Mockito 5 + AssertJ — Backend unit tests
- Spring Boot Test + Spring Security Test — Web/integration tests
- Testcontainers (PostgreSQL) — Backend integration tests (`backend/pom.xml`)
- Vitest 1.3 — Frontend tests (`frontend/vite.config.ts`)
- @testing-library/react 14.3 + user-event — Component tests

**Build / Dev tooling:**
- Flyway — Database migrations (`backend/src/main/resources/db/migration/`)
- MapStruct 1.6 — DTO mapping
- JaCoCo 0.8.12 — Backend coverage (60% instruction / 45% branch minimum, `backend/pom.xml`)
- Spotless 2.43 with Google Java Format (AOSP) — Backend formatter (`backend/pom.xml`)
- ESLint 8.57 — Frontend linting (`frontend/.eslintrc.cjs`)
- PostCSS 8.4 — Tailwind preprocessing

## Key Dependencies

**Backend (critical):**
- jjwt 0.13.0 — JWT signing/verification
- PostgreSQL JDBC driver — DB driver
- Flyway Core — Schema migrations
- OpenPDF 2.0.3 — PDF report export
- Apache POI 5.2.5 — Excel import/export
- Lombok — Boilerplate reduction (annotations across entities/services)

**Frontend (critical):**
- axios 1.6 — HTTP client (`frontend/src/api/client.ts`)
- react-i18next — Internationalization (`frontend/src/i18n/`)
- date-fns — Date formatting
- zod — Form/runtime validation

## Configuration

**Backend environment:**
- Profile-driven config in `backend/src/main/resources/application.yml`
- Profiles: `development` (Swagger UI on, console logs), `production` (file logs in `/var/log/fintrack`, Swagger off)
- Required env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_EXPIRY_MINUTES`, `JWT_REFRESH_EXPIRY_DAYS`, `SMTP_*`, `COINGECKO_API_KEY`, `EXCHANGE_RATE_API_KEY`
- Optional: `CLAUDE_API_KEY`, `CLAUDE_ENABLED`, `PUSH_VAPID_*`, `AUTHELIA_*`

**Frontend build-time env (Vite):**
- `VITE_API_BASE_URL` (default `/api/v1`)
- `VITE_WS_URL` (default `ws://localhost/ws`)
- `VITE_APP_NAME`

**Build configs:**
- `backend/pom.xml`, `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/tailwind.config.ts`, `frontend/postcss.config.js`, `frontend/.eslintrc.cjs`

**Secret template:**
- `.env.example` (root) — copy to `.env` for Docker Compose

## Platform Requirements

**Development:**
- Windows / Linux / macOS with Docker Desktop or native Docker
- JDK 21 (Temurin) — JAVA_HOME may not be set; use the JDK bundled with the VS Code Java extension when running `mvnw` locally
- Node 20 LTS, npm 10
- Docker Engine + Docker Compose v2

**Production:**
- Linux host with Docker Engine + Docker Compose
- ~4 GB RAM (backend + frontend + Postgres + Redis + Prometheus + Grafana)
- Public DNS A record + Let's Encrypt for HTTPS (`scripts/ssl-setup.sh`)
- systemd unit available for auto-start (`scripts/fintrack.service`)

---

*Stack analysis: 2026-05-03*
*Update after major dependency or framework changes*
