---
name: turkish-finance
description: Turkish financial instruments, regulations, and terminology used in this project
---

# Turkish Finance Context

## Portfolio Assets in This Project

| Symbol | Full Name | Type | Price Source |
|--------|-----------|------|-------------|
| BTC | Bitcoin | CRYPTO | CoinGecko |
| ETH | Ethereum | CRYPTO | CoinGecko |
| TTA | Altın Yatırım Fonu | GOLD | TEFAS |
| ITP | Teknoloji Fonu | FUND | TEFAS |
| TIE | BIST 30 ETF | FUND | TEFAS |
| TMG | Yabancı Hisse Fonu | FUND | TEFAS |
| TI1 | Para Piyasası Fonu | FUND | TEFAS |
| ABE | S&P 500 (BES) | FUND | TEFAS |
| AH5 | Hisse Senedi (BES) | FUND | TEFAS |
| BHT | Teknoloji (BES) | FUND | TEFAS |
| BGL | Altın (BES) | FUND | TEFAS |
| AH3 | Eurobond (BES) | FUND | TEFAS |

## BES (Bireysel Emeklilik Sistemi)

BES is Turkey's private pension system:
- Monthly contribution: 13,000 TRY
- Government contribution rate: 20% (since 2026)
- Vesting schedule: 0-3 years: 0%, 3-6 years: 15%, 6-10 years: 35%, 10+ years: 60%, retirement (56+ and 10 years): 100%
- Fund change limit: 12 times/year
- Long-term horizon: minimum 10 years

BES transactions use `txn_type = BES_CONTRIBUTION` — kept separate from regular investment transactions.
BES holdings belong to a separate portfolio with `portfolio_type = BES`.

## Currency Formatting (Turkish)

Turkey uses:
- Currency symbol: ₺ (before number or after, both acceptable, we use: ₺45.000,00)
- Thousands separator: `.` (dot)
- Decimal separator: `,` (comma)
- This is OPPOSITE to US formatting — be careful in formatters

```typescript
// CORRECT Turkish formatting:
formatTRY(45000.50) → "₺45.000,50"

// WRONG (US style):
// "₺45,000.50"
```

## TEFAS Fund Prices

TEFAS (Türkiye Elektronik Fon Alım Satım Platformu) is the official Turkish fund marketplace.
Fund prices are daily NAV (Net Asset Value) — updated once per business day.
Use yesterday's price for weekend/holiday queries.

## Investment Strategy Context (the owner's strategy)

This information helps AI analysis make relevant suggestions:

**Bireysel Portfolio:**
- 1-3 year horizon (planning for a rights offering — "bedelli")
- Target allocation: BTC 5%, ETH 2%, TTA(Gold) 45%, ITP 15%, TIE 18%, TMG 15%
- Liquidity reserve: TI1 (20-50K TRY, not counted in allocation)
- Crypto rules: BTC at -50% → sell 50%; BTC at +12% → take profits
- Before rights offering (1 year prior): zero crypto, reduce equity, move to gold + money market

**BES Portfolio:**
- 10+ year horizon
- Target: ABE(S&P500) 30%, AH5(equity) 20%, BHT(tech) 15%, BGL(gold) 20%, AH3(eurobond) 15%
- Strategy: 65% growth / 35% protection
- Review quarterly, change funds only on significant deviation

## Turkish Financial Terms (for UI and API field names)

| English | Turkish | Use in UI |
|---------|---------|-----------|
| Portfolio | Portföy | "Portföy" |
| Holdings | Varlıklar | "Varlıklar" |
| Transaction | İşlem | "İşlem" |
| Buy | Alım | "Alım" |
| Sell | Satım | "Satım" |
| Profit/Loss | Kar/Zarar | "K/Z" |
| Return | Getiri | "Getiri" |
| Income | Gelir | "Gelir" |
| Expense | Gider | "Gider" |
| Budget | Bütçe | "Bütçe" |
| Bill | Fatura | "Fatura" |
| Due date | Vade tarihi | "Vade" |
| Savings rate | Tasarruf oranı | "Tasarruf Oranı" |
| Net worth | Net varlık | "Net Varlık" |
| Allocation | Dağılım | "Dağılım" |
| Deviation | Sapma | "Sapma" |
| Snapshot | Anlık görüntü | "Log" (we call it this in UI) |
| Pension | Emeklilik | "Emeklilik" |
