# Phase 1 Kickoff — FinTrack Pro

## Oku önce

Bu projeye başlamadan önce şu dosyaları sırayla oku:
1. `CLAUDE.md` — proje özeti, stack, kurallar
2. `docs/ARCHITECTURE.md` — sistem mimarisi
3. `docs/DATABASE.md` — veritabanı şeması
4. `docs/SECURITY.md` — JWT auth modeli
5. `docs/DEV_SETUP.md` — komutlar
6. `tasks/TODO.md` — ne yapılacak

Skills dosyaları ilgili olduğunda oku:
- `.claude/skills/spring-patterns.md` — backend yazarken
- `.claude/skills/react-patterns.md` — frontend yazarken
- `.claude/skills/turkish-finance.md` — domain context için
- `.claude/skills/infra-patterns.md` — Docker/Nginx yazarken

---

## Phase 1 Görevi

**Amaç:** Projeyi çalışır hale getir. Login olunabilen, boş dashboard gösteren, Docker ile ayağa kalkan minimum sistem.

### Adımlar (sırayla yap)

#### 1. Infrastructure
- `docker-compose.yml` — tüm 5 servis
- `nginx/nginx.conf` — HTTP routing (SSL yok, local dev)
- `.env.example` — tüm değişkenler
- `scripts/fintrack.service` — systemd (Windows'ta lazım olmaz ama hazır olsun)
- `scripts/backup.sh`

#### 2. Backend — Temel Kurulum
- `backend/pom.xml` — tüm dependency'ler
- `backend/src/main/resources/application.yml`
- `FinTrackApplication.java`
- Flyway migration dosyaları:
  - `V1__initial_schema.sql` (docs/DATABASE.md'deki tam şema)
  - `V2__seed_assets.sql` (BTC, ETH, TTA, ITP, TIE, TMG, TI1, ABE, AH5, BHT, BGL, AH3)
  - `V3__seed_categories.sql` (Türkçe gelir/gider kategorileri)
- Tüm JPA entity sınıfları (`common/entity/`)
- Global exception handler
- `GET /api/health` endpoint

#### 3. Backend — Auth Modülü
- Tüm dosyalar `.claude/skills/spring-patterns.md`'deki pattern'e göre
- JWT util, filter, security config, user details service
- Auth controller (register, login, refresh, logout, /me)
- Auth service + refresh token service
- Auth DTOs (Java records)

#### 4. Frontend — Temel Kurulum
- `frontend/package.json` — tüm dependency'ler
- `vite.config.ts` — proxy config
- `tailwind.config.ts` — custom colors
- shadcn/ui kurulum (components.json + temel komponentler: Button, Input, Card, Table, Dialog, Toast, Skeleton)
- `src/api/client.ts` — axios + interceptor'lar
- `src/store/auth.store.ts`
- `src/types/` — tüm TypeScript interface'leri
- `src/utils/formatters.ts` — TRY, %, tarih (Türkçe format)
- `src/utils/calculations.ts`

#### 5. Frontend — Auth
- `LoginPage.tsx` — kullanıcı adı + şifre formu
- Route protection (login olmadan dashboard'a girilemesin)
- Token refresh logic (client.ts interceptor'da)
- İlk login sonrası boş dashboard'a yönlendir

#### 6. Backend Dockerfile + Frontend Dockerfile

---

## Bittiğinde test et

```bash
# 1. Kopyala .env.example → .env, değerleri doldur
cp .env.example .env

# 2. Docker ile başlat
docker compose up -d

# 3. Sağlık kontrolü
curl http://localhost/api/health
# Beklenen: {"status":"UP"}

# 4. Register
curl -X POST http://localhost/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com","password":"password123"}'
# Beklenen: 201 + token'lar

# 5. Login
curl -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"password123"}'
# Beklenen: 200 + token'lar

# 6. Tarayıcı
open http://localhost
# Beklenen: login sayfası görünsün, giriş yapınca dashboard açılsın
```

---

## Önemli Notlar

- Tüm entity'lerde UUID kullan, Long id değil
- Türkçe para formatı: ₺45.000,00 (nokta=binlik, virgül=ondalık)
- Para sahaları `BigDecimal` — float/double asla
- Timestamp'ler `Instant` — LocalDateTime değil
- Her yeni tablo için Flyway migration, entity'yi elle güncelleme
- `.env` asla commit'leme
- Bitti mi? `tasks/TODO.md`'yi güncelle

## Sonraki Phase

Phase 1 bittikten sonra Phase 2'ye geç: Portfolio modülü + canlı fiyatlar.
`tasks/TODO.md`'de Phase 2 maddeleri var.
