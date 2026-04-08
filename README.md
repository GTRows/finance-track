# 💰 FinTrack Pro

Personal finance & investment tracking system. Self-hosted, open-source, production-ready.

## Features

- **Investment Portfolio** — track stocks, crypto, gold, BES; live prices via API
- **Income & Expenses** — monthly budget tracking with full log history
- **Bill Tracker** — recurring bills with due-date reminders
- **Live Dashboard** — real-time charts, P&L, allocation breakdowns
- **External Access** — JWT auth + HTTPS via Nginx, safe to expose publicly
- **Auto-start** — runs on boot via Docker `restart: always`
- **Claude Integration** — AI-powered analysis endpoint (optional)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.2, Spring Security 6 |
| Frontend | React 18, Vite, Tailwind CSS, shadcn/ui, Recharts |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Proxy | Nginx (SSL termination, rate limiting) |
| Container | Docker & Docker Compose |
| Auth | JWT (access + refresh tokens) |
| Realtime | WebSocket (live price updates) |
| Price APIs | CoinGecko (crypto), ExchangeRate-API (FX), Yahoo Finance (stocks) |

## Quick Start

```bash
# 1. Clone & configure
git clone https://github.com/yourname/fintrack.git
cd fintrack
cp .env.example .env
# Edit .env with your settings

# 2. Start everything
docker compose up -d

# 3. Open browser
open http://localhost  # or https://yourdomain.com
```

## Project Structure

```
fintrack/
├── backend/              # Spring Boot API
│   └── src/main/java/com/fintrack/
│       ├── config/       # Security, CORS, WebSocket config
│       ├── controller/   # REST endpoints
│       ├── dto/          # Request/Response DTOs
│       ├── entity/       # JPA entities
│       ├── repository/   # Spring Data repositories
│       ├── service/      # Business logic
│       ├── security/     # JWT filter, UserDetailsService
│       └── scheduler/    # Price sync, bill reminders
├── frontend/             # React + Vite app
│   └── src/
│       ├── api/          # Axios client + endpoints
│       ├── components/   # UI components by feature
│       ├── pages/        # Route-level pages
│       ├── store/        # Zustand state
│       └── types/        # TypeScript types
├── nginx/                # Reverse proxy config
├── scripts/              # Setup & maintenance scripts
├── docker-compose.yml    # Full stack orchestration
├── docker-compose.prod.yml # Production overrides
└── .env.example          # Environment template
```

## Environment Variables

See `.env.example` for all required variables. Key ones:

```env
JWT_SECRET=your-256-bit-secret
POSTGRES_PASSWORD=strongpassword
COINGECKO_API_KEY=your-key        # optional, free tier works
EXCHANGE_RATE_API_KEY=your-key    # optional
```

## Auto-start on Boot

Docker Compose is configured with `restart: always` — containers restart automatically after system reboot.

For systemd management:
```bash
sudo cp scripts/fintrack.service /etc/systemd/system/
sudo systemctl enable fintrack
sudo systemctl start fintrack
```

## External Access (HTTPS)

1. Point your domain's DNS to your server IP
2. Edit `nginx/nginx.conf` with your domain
3. Run `scripts/ssl-setup.sh` to get a Let's Encrypt certificate
4. Open port 443 on your router/firewall

## License

MIT — free to use, fork, and modify.
