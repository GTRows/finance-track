# Dev Setup

## Prerequisites

- Docker Desktop (Windows) — https://www.docker.com/products/docker-desktop
- Java 21 (for running backend outside Docker) — https://adoptium.net
- Node.js 20+ (for running frontend outside Docker) — https://nodejs.org
- Git

## First-Time Setup

```bash
# 1. Clone repo
git clone https://github.com/yourname/fintrack.git
cd fintrack

# 2. Copy env file and fill in values
cp .env.example .env
# Open .env and set at minimum:
#   JWT_SECRET=<random 64 chars>
#   POSTGRES_PASSWORD=<any password for local dev>
#   REDIS_PASSWORD=<any password for local dev>

# 3. Start all services
docker compose up -d

# 4. Check everything is running
docker compose ps
# Should show: nginx, frontend, backend, postgres, redis — all "Up"

# 5. Open browser
# http://localhost

# 6. Register first user
# Click "Kayıt Ol" on login page
```

## Daily Development

### Option A — Full Docker (recommended for integration testing)
```bash
docker compose up -d
# Edit code → rebuild specific service:
docker compose up -d --build backend   # after Java changes
docker compose up -d --build frontend  # after React changes
```

### Option B — Hybrid (DB in Docker, code runs locally — faster dev loop)
```bash
# Start only DB + Redis
docker compose up -d postgres redis

# Run backend locally
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=development

# Run frontend locally (in another terminal)
cd frontend
npm install        # first time only
npm run dev        # starts on http://localhost:5173
```

In Option B, frontend talks directly to `http://localhost:8080/api` (no Nginx).
Set `VITE_API_BASE_URL=http://localhost:8080/api` in your local `.env`.

## Useful Commands

```bash
# View logs
docker compose logs -f backend      # Spring Boot logs
docker compose logs -f frontend     # Vite logs
docker compose logs -f nginx        # Nginx access/error logs

# Connect to PostgreSQL directly
docker compose exec postgres psql -U fintrack -d fintrack

# Connect to Redis
docker compose exec redis redis-cli -a $REDIS_PASSWORD

# Run backend tests
cd backend && ./mvnw test

# Run backend tests with coverage
cd backend && ./mvnw test jacoco:report

# Run frontend tests
cd frontend && npm run test

# Frontend type check
cd frontend && npm run typecheck

# Frontend lint
cd frontend && npm run lint

# Check Flyway migration status
cd backend && ./mvnw flyway:info -Dflyway.url=jdbc:postgresql://localhost:5432/fintrack -Dflyway.user=fintrack -Dflyway.password=<pass>

# Full rebuild (when dependencies change)
docker compose build --no-cache
docker compose up -d

# Stop everything
docker compose down

# Stop and delete all data (⚠ destructive)
docker compose down -v
```

## Environment Variables

All variables documented in `.env.example`.

For local development, minimum required:
```env
JWT_SECRET=dev-secret-at-least-32-chars-long-here
POSTGRES_PASSWORD=devpassword
REDIS_PASSWORD=devpassword
VITE_API_BASE_URL=http://localhost/api     # if using Docker
# or
VITE_API_BASE_URL=http://localhost:8080/api  # if running backend locally
```

## Adding a New Feature

1. **DB change?** → Create `backend/src/main/resources/db/migration/V{n+1}__description.sql`
2. **New backend module** → Create `backend/src/main/java/com/fintrack/{feature}/` package
3. **New frontend feature** → Create `frontend/src/components/{feature}/` directory
4. **New API endpoint** → Document in `docs/API.md` first, then implement
5. **Completed a task** → Update `tasks/TODO.md`
6. **Made a mistake and fixed it** → Add lesson to `tasks/LESSONS.md`

## Production Deployment (VPS)

```bash
# On the VPS (Linux):
git clone https://github.com/yourname/fintrack.git
cd fintrack
cp .env.example .env
# Fill in production values (strong passwords, real JWT secret, domain)

# Setup SSL (requires domain pointing to server)
chmod +x scripts/ssl-setup.sh
./scripts/ssl-setup.sh

# Enable auto-start
sudo cp scripts/fintrack.service /etc/systemd/system/
sudo systemctl enable fintrack
sudo systemctl start fintrack

# Verify
sudo systemctl status fintrack
docker compose ps
```

## Backup

```bash
# Manual backup
./scripts/backup.sh

# Setup automatic daily backup (cron)
crontab -e
# Add: 0 2 * * * /path/to/fintrack/scripts/backup.sh
```

Backups saved to `./backups/` directory. Last 30 days kept.
