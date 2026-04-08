# Price APIs & Integration

All price APIs are free tier. No credit card required.

## CoinGecko (Crypto Prices)

**URL:** https://api.coingecko.com/api/v3
**Free tier:** 30 calls/minute, no API key required (add key to avoid limits)
**Registration:** https://www.coingecko.com/en/api

### Endpoint used
```
GET /simple/price?ids=bitcoin,ethereum&vs_currencies=try,usd&include_24hr_change=true
```

### Symbol → CoinGecko ID mapping
```java
// In CoinGeckoClient.java
Map<String, String> SYMBOL_TO_ID = Map.of(
    "BTC", "bitcoin",
    "ETH", "ethereum"
);
```

### Response example
```json
{
  "bitcoin": {
    "try": 6788000,
    "usd": 103500,
    "try_24h_change": -1.24
  }
}
```

## TEFAS (Turkish Investment Funds)

**URL:** https://www.tefas.gov.tr
**API endpoint:** `https://www.tefas.gov.tr/api/DB/BindHistoryInfo`
**Free:** Yes, public government data
**No API key needed**

### Request
```http
POST https://www.tefas.gov.tr/api/DB/BindHistoryInfo
Content-Type: application/x-www-form-urlencoded

fontip=YAT&bastarih=04.04.2026&bittarih=08.04.2026&fonkod=TTA
```

Parameters:
- `fontip`: YAT = Yatırım Fonu
- `bastarih`: start date (DD.MM.YYYY)
- `bittarih`: end date (DD.MM.YYYY)
- `fonkod`: fund code

### Fund codes we track
```
TTA  — Altın (Gold fund)
ITP  — Teknoloji fonu
TIE  — BIST 30 ETF
TMG  — Yabancı hisse
TI1  — Para piyasası (money market)
ABE  — S&P 500 (BES)
AH5  — Hisse senedi (BES)
BHT  — Teknoloji (BES)
BGL  — Altın (BES)
AH3  — Eurobond (BES)
```

### Response
```json
{
  "data": [
    {
      "FONKODU": "TTA",
      "FIYAT": "1.845231",
      "TARIH": "08.04.2026"
    }
  ]
}
```

## ExchangeRate-API (Forex)

**URL:** https://v6.exchangerate-api.com
**Free tier:** 1,500 requests/month
**Registration:** https://www.exchangerate-api.com (free, no credit card)

With 5-minute sync interval: 288 requests/day × 30 = 8,640/month → exceeds free tier.
**Solution:** Sync forex every 30 minutes instead of 5 minutes (86 requests/day × 30 = 2,580/month ✅)

### Endpoint
```
GET https://v6.exchangerate-api.com/v6/{API_KEY}/pair/USD/TRY
```

### Response
```json
{
  "result": "success",
  "base_code": "USD",
  "target_code": "TRY",
  "conversion_rate": 65.62
}
```

## Sync Scheduler Logic

```java
// PriceSyncScheduler.java

@Scheduled(fixedDelayString = "${price-api.sync-interval-minutes}0000") // every 5min
public void syncCryptoPrices() { ... }   // BTC, ETH via CoinGecko

@Scheduled(fixedDelayString = "300000")  // every 5min
public void syncFundPrices() { ... }     // TTA, ITP, TIE, TMG, TI1, ABE, AH5, BHT, BGL, AH3 via TEFAS

@Scheduled(fixedDelayString = "1800000") // every 30min
public void syncForexPrices() { ... }    // USD/TRY, EUR/TRY via ExchangeRate-API
```

After each sync:
1. Update `assets.price` and `assets.price_updated_at` in PostgreSQL
2. Insert row into `price_history`
3. Set Redis key `price:{symbol}` with TTL matching sync interval
4. Broadcast to WebSocket `/topic/prices`

## Error Handling

If an external API fails:
- Log warning, don't crash the scheduler
- Use last cached value from Redis (stale is better than nothing)
- After 3 consecutive failures: send in-app notification to user
- Price shows "⚠ stale" indicator in UI if `price_updated_at` > 15 minutes ago

## Future: BIST Individual Stocks

If user wants to track individual BIST stocks (e.g. THYAO, ASELS):
- Use `https://api.bigpara.hurriyet.com.tr/borsa/hisse/...` (unofficial, may break)
- Or integrate with a broker API (İş Yatırım, Garanti BBVA Yatırım)
- Out of scope for initial version
