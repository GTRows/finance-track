---
phase: 28-rebalance-and-emergency-fund
plan: 02
subsystem: portfolio
tags: [rebalance, allocation, drift, transactions, redis, audit]

requires:
  - phase: 18
    plan: target-allocation
    provides: AllocationService.summarize -- the bucket-delta source
  - phase: 25
    plan: 01
    provides: InvestmentTransactionRecordedEvent + HoldingProjectionListener -- the holding-update engine
  - phase: 27
    plan: 02
    provides: Account entity + AccountRepository ownership scoping
  - phase: 27
    plan: 03
    provides: account_id FK + AccountBalanceListener@AFTER_COMMIT -- the cash rollup
  - phase: 27
    plan: 04
    provides: BankCsvImportService preview+commit canonical pattern -- this plan mirrors it byte-for-byte
  - phase: 26
    plan: 01
    provides: Tracing observation handler -- @Observed boundaries plug into Tempo for free
  - phase: 28
    plan: 01
    provides: V45 user_settings precedent -- V46 single-column-CHECK migration follows the same shape

provides:
  - V46__user_settings_rebalance_drift_threshold.sql -- one NUMERIC(5,2) column with single-column CHECK (BETWEEN 0.10 AND 10.00, default 1.00)
  - UserSettings.rebalanceDriftThresholdPercent BigDecimal field with @Builder.Default
  - com.fintrack.portfolio.rebalance package: RebalanceService (preview + commit + projection algorithm), RebalanceController, four DTOs (RebalancePreviewRequest, RebalanceCommitRequest, RebalanceSuggestion, RebalancePreview, RebalanceCommitResult), RebalanceProposalStore Redis adapter
  - POST /api/v1/portfolios/{portfolioId}/rebalance/preview + POST /api/v1/portfolios/{portfolioId}/rebalance/commit endpoints
  - PUT /api/v1/settings/rebalance-threshold endpoint folded into the existing SettingsController
  - AuditAction constants REBALANCE_PREVIEWED + REBALANCE_COMMITTED + USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED
  - RebalanceConflictException -- new BusinessRuleException subclass mapped to 409 by GlobalExceptionHandler so stale-preview and double-commit return Conflict instead of Bad Request
  - @Observed(name=portfolio.rebalance.preview) + @Observed(name=portfolio.rebalance.commit) tracing boundaries
  - RebalanceCard frontend sub-card on PortfolioDetailPage with drift-threshold slider, account picker, generate button, suggestion table, and commit button
  - rebalance.api.ts + useRebalance.ts (three React Query mutations) + rebalance.types.ts
  - rebalance.* i18n namespace in tr.json + en.json (header, description, slider, account picker, button, summary, columns, actions, warnings, errors, success toast)

affects: []

tech-stack:
  added: []
  patterns:
    - "Preview + commit pattern mirroring 27-04 byte-for-byte. RebalanceService.preview is @Transactional(readOnly=true), returns a RebalancePreview with a server-side proposalId (UUID v4), stores the canonical hash in Redis with a 30-minute TTL. Commit is @Transactional, recomputes suggestions from the live state, compares hashes, and on match materialises one InvestmentTransaction per ticked row through InvestmentTransactionService.record(...) so the existing 25-01 holding projection listener and 27-03 account-balance listener fire normally. The 24-hour `rebalance:committed:<userId>:<proposalId>` sentinel prevents replay attempts."
    - "Idempotency via Redis-backed proposalId, NOT a fingerprint column on investment_transactions. Bank CSV import (27-04) used a SHA-256 fingerprint on the persisted row for cross-session dedupe (operator might re-upload the same CSV next month); rebalance proposals are session-scoped (operator generates a preview, ticks rows, commits within minutes), so a Redis TTL key is sufficient and avoids polluting investment_transactions with a column that has no other purpose."
    - "Hash check across preview and commit. canonicalHash(portfolioId, accountId, suggestions[]) = SHA-256 hex of the JSON-canonicalised tuple of (portfolioId, accountId, [for each suggestion: assetId, action, quantity, estimatedPriceTry]). Preview computes once and stores. Commit recomputes from the LIVE state and compares. Mismatch -> RebalanceConflictException(REBALANCE_PROPOSAL_STALE) 409. Load-bearing because between preview and commit (a) prices may have moved, (b) the operator may have added/removed a holding via another tab, (c) the operator may have committed a different rebalance against the same account."
    - "Service ownership guards. portfolioRepository.findByIdAndUserIdAndActiveTrue gates every preview + commit. accountRepository.findByIdAndUserIdAndArchivedFalse gates every preview + commit. Absent or archived account -> BusinessRuleException(ACCOUNT_NOT_OWNED) (24-08 audit-failure-first pattern). Mirrors BankCsvImportService byte-for-byte."
    - "Bucket-to-holding projection. Step 1: per-bucket deltas via AllocationService.summarize semantics. Step 2: filter buckets where |drift| <= threshold. Step 3: per surviving bucket, OVERWEIGHT -> pro-rata SELL across the bucket's holdings (sellShare = holdingValue / bucketTotalValue, capped at holdingValue); UNDERWEIGHT -> single-holding BUY on the highest-value existing holding (alphabetical tiebreak by symbol). Empty underweight bucket emits a NO_HOLDING_TO_BUY informational row. Step 4: cash-scaling on BUY rows only; SELL rows are NEVER scaled because they replenish cash. Step 5: STOCK/FUND integer-truncation via RoundingMode.DOWN; truncated-to-zero rows gain a QUANTITY_BELOW_LOT warning. Step 6: projectedDriftAfterPercent computed honestly from the post-truncation state."
    - "RebalanceConflictException -- new BusinessRuleException subclass that GlobalExceptionHandler maps to HTTP 409. Created so stale-preview and double-commit return Conflict instead of the default 400. Other rebalance errors (REBALANCE_NO_TARGETS, REBALANCE_THRESHOLD_OUT_OF_RANGE, REBALANCE_SELECTION_OUT_OF_RANGE) stay on the default 400 mapping."
    - "Two @Observed annotations on the service-orchestrator methods. @Observed(name=portfolio.rebalance.preview) + @Observed(name=portfolio.rebalance.commit) wire to the 26-01 observation registry so Tempo sees the rebalance batch as a top-level span. The 26-01 servlet observation handler auto-instruments every @RestController method; the explicit @Observed on the service layer captures the meaningful work (allocation walk + projection + cash scaling)."
    - "Settings endpoint folded into the existing SettingsController. PUT /api/v1/settings/rebalance-threshold mounts on SettingsController per the 28-01 EmergencyFundController.updateConfig precedent. Bean Validation (@DecimalMin('0.10') @DecimalMax('10.00') @Digits(2,2)) handles obvious cases; the service-level guard catches direct internal calls and emits the audit success/failure pair."
    - "Frontend card placement, NOT route. RebalanceCard mounts inline on PortfolioDetailPage AFTER the AllocationTargets card (visual flow: AllocationChart -> AllocationTargets (drift visualisation) -> RebalanceCard (drift -> action)). Gated behind allocation.configured && holdings.length > 0. NO new route under /portfolio/:id/rebalance. Per the 28-01 EmergencyFundSection precedent."
    - "Frontend hook layering. useRebalancePreview is a useMutation (NOT useQuery -- the preview is server-state-mutating from a Redis perspective, and the operator triggers it explicitly). useRebalanceCommit invalidates ['portfolios', portfolioId, 'holdings'] + ['transactions', portfolioId] + ['allocation', portfolioId] + ['accounts'] + ['accounts', 'totals']. useUpdateRebalanceThreshold invalidates ['settings'] and is debounced 400ms by the slider component."

key-files:
  created:
    - backend/src/main/resources/db/migration/V46__user_settings_rebalance_drift_threshold.sql
    - backend/src/main/java/com/fintrack/portfolio/rebalance/RebalanceService.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/RebalanceController.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/RebalanceProposalStore.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/dto/RebalancePreviewRequest.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/dto/RebalanceCommitRequest.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/dto/RebalanceSuggestion.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/dto/RebalancePreview.java
    - backend/src/main/java/com/fintrack/portfolio/rebalance/dto/RebalanceCommitResult.java
    - backend/src/main/java/com/fintrack/common/exception/RebalanceConflictException.java
    - backend/src/main/java/com/fintrack/settings/dto/UpdateRebalanceThresholdRequest.java
    - backend/src/main/java/com/fintrack/settings/dto/UpdateRebalanceThresholdResponse.java
    - backend/src/test/java/com/fintrack/portfolio/rebalance/RebalanceServiceTest.java
    - backend/src/test/java/com/fintrack/portfolio/rebalance/RebalanceControllerWebMvcTest.java
    - backend/src/test/java/com/fintrack/portfolio/rebalance/RebalanceProposalStoreTest.java
    - backend/src/test/java/com/fintrack/settings/SettingsServiceRebalanceThresholdTest.java
    - frontend/src/api/rebalance.api.ts
    - frontend/src/types/rebalance.types.ts
    - frontend/src/hooks/useRebalance.ts
    - frontend/src/hooks/useRebalance.test.tsx
    - frontend/src/components/portfolio/RebalanceCard.tsx
    - frontend/src/components/portfolio/RebalanceCard.test.tsx
    - .planning/phases/28-rebalance-and-emergency-fund/28-02-SUMMARY.md
  modified:
    - backend/src/main/java/com/fintrack/common/entity/UserSettings.java
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java
    - backend/src/main/java/com/fintrack/settings/SettingsService.java
    - backend/src/main/java/com/fintrack/settings/SettingsController.java
    - backend/src/test/java/com/fintrack/settings/UserSettingsRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/settings/SettingsControllerWebMvcTest.java
    - frontend/src/pages/PortfolioDetailPage.tsx
    - frontend/src/i18n/locales/tr.json
    - frontend/src/i18n/locales/en.json
    - docs/OPERATIONS.md
    - .planning/STATE.md
    - .planning/ROADMAP.md
  deliberately-untouched:
    - .env.example -- project deny rule; no new env vars
    - docker-compose.yml -- pre_guard_release_files.py PreToolUse hook; no infra changes
    - CHANGELOG.md -- pre_guard_release_files.py covers it; per the 26-01 / 26-02 / 26-03 / 27-01 / 27-02 / 27-03 / 27-04 / 28-01 precedent, the changelog entry is described in this SUMMARY and applied by the release flow
    - backend/pom.xml -- no new Maven dep
    - package.json + package-lock.json -- no new npm dep
    - frontend/openapi.json + frontend/src/api/openapi.types.ts -- regen script (scripts/regen-openapi.sh) still fails on the pre-existing 26-01 OpenTelemetry sdk-autoconfigure ComponentLoader NoClassDefFoundError. The new endpoint surface is exercised end-to-end by RebalanceControllerWebMvcTest + SettingsControllerWebMvcTest

key-decisions:
  - "Asset-type bucket granularity. The existing portfolio_allocation_targets table is keyed (portfolio_id, asset_type) -- targets exist at the asset-type level (CRYPTO / FUND / CURRENCY / GOLD / STOCK / OTHER), NOT per asset. The rebalance executor preserves this granularity: it computes deltas per asset-type bucket and projects them onto holdings within each bucket. Per-asset target weights (PortfolioHolding.targetWeight exists but is unused) are OUT OF SCOPE -- a per-asset target schema rewrite is orthogonal to the executor."
  - "Pro-rata SELL distribution within an OVERWEIGHT bucket. The simplest correct projection. Alternative would be 'sell from highest-cost-basis holding first to harvest losses' (tax-loss-harvesting heuristic) -- explicitly out of scope; the 27-01 tax helper handles the realised-gain side. The pro-rata rule has a clean property: after applying the suggestions, every holding within an overweight bucket retains its WITHIN-BUCKET share, only the TOTAL bucket value drops."
  - "Single-holding BUY concentration in an UNDERWEIGHT bucket. Concentrate the entire BUY amount on the highest-value existing holding (alphabetical tiebreak) rather than splitting across the bucket's holdings. Reasoning: (a) commission/fee minimisation -- one trade beats N small trades; (b) the operator can always uncheck the row and add manual fine-grained trades; (c) splitting across N holdings within a bucket pro-rata would dilute meaning -- the operator picks 'I want 30% in stocks', not 'I want 10% in THYAO and 20% in GARAN'."
  - "No new asset introduction. The executor CANNOT recommend buying an asset the operator hasn't already added to the portfolio. An empty UNDERWEIGHT bucket gets a single NO_HOLDING_TO_BUY informational row. Load-bearing for safety -- the executor is a corrective tool, not a stock picker; recommending which crypto to buy is out of scope and would require a research-grade asset universe."
  - "Cash scaling is proportional, not iterative. When requiredBuyTry > availableCashTry, every BUY row is scaled by the same ratio (availableCashTry / requiredBuyTry). Alternative would be iterative ('fill the highest-priority bucket first, then the next') -- explicit out of scope; proportional scaling is the operator-intent-preserving choice (the operator wanted to bring all underweight buckets toward target; partial-cash should preserve relative bucket priorities, not arbitrarily zero out the smallest buckets)."
  - "Quantity quirks pinned to asset-type, NOT to a per-asset configuration. STOCK and FUND truncate to integer; CRYPTO / GOLD / CURRENCY / OTHER use scale=8. This is a simplification -- real markets have lot sizes (stocks trade in 100-share lots in some markets), but BIST trades single-share lots and BES funds redeem in fractional shares. Per-asset min_lot_size columns are out of scope. The integer-truncation rule prevents the executor from suggesting 'sell 3.42 shares of THYAO' which the operator would have to round manually."
  - "Drift threshold is per-user (not per-portfolio). A single setting applies across all portfolios. Per-portfolio thresholds are a polish item; the operator can override at preview time via an optional request field driftThresholdOverride (per-call, not persisted) in case they want a tighter threshold on a specific portfolio for a one-off rebalance."
  - "30-minute proposal TTL. Long enough for the operator to step away from the laptop, eat lunch, come back, and commit. Short enough that prices haven't drifted unbearably (live prices update every 5 minutes at most). The hash check fences against the rare case where prices DID drift within 30 minutes -- the commit returns 409 with PROPOSAL_STALE and the operator re-previews."
  - "24-hour committed marker. A double-commit attempt (operator clicks twice rapidly, browser network retry, etc.) within 24h returns a clean 409 PROPOSAL_ALREADY_COMMITTED. Beyond 24h the marker expires; if the operator is somehow still holding a stale proposalId after 24h, the commit returns 404 PROPOSAL_NOT_FOUND instead -- both cases prevent double-commit, the difference is the error message clarity."
  - "RebalanceConflictException as a new BusinessRuleException subclass. Introduced because GlobalExceptionHandler defaults BusinessRuleException to HTTP 400, but stale-preview and double-commit are conceptually 409 Conflict (the request was valid but the cached server state diverges). The new subclass handler runs before the parent BusinessRuleException handler so stale + already-committed return 409 while every other rebalance error stays on 400."

deferred-enhancements:
  - "Per-portfolio drift threshold. The plan ships a per-user setting; per-portfolio overrides need a portfolios-level threshold column or a separate join table. Out of scope for the v1 executor."
  - "Per-asset allocation targets. The bucket granularity is asset-type. Per-asset targets need a schema rewrite (PortfolioHolding.targetWeight is unused today) and a corresponding allocation chart redesign."
  - "Tax-loss harvesting heuristic on SELL. The 27-01 tax helper handles realised-gain bookkeeping; an executor-side 'sell from highest-cost-basis first' heuristic would couple the rebalance flow to the tax helper and is out of scope."
  - "Iterative cash scaling. The executor scales proportionally; an iterative 'fill highest-priority bucket first' algorithm would need a priority ranking that the operator does not currently express."
  - "Past-rebalance history view. Audit log queries against REBALANCE_PREVIEWED / REBALANCE_COMMITTED + the rebalance:<proposalId> notes prefix already cover the forensic question. A dedicated history table is out of scope."

deviations-from-plan: []

verification-notes:
  - "Backend unit + slice tests: RebalanceServiceTest (22 cases) + RebalanceControllerWebMvcTest (11 cases including 9 plan-mandated + 2 extra) + RebalanceProposalStoreTest (5 cases) + SettingsServiceRebalanceThresholdTest (4 cases) + extended SettingsControllerWebMvcTest (+3 cases) + extended UserSettingsRepositoryDataJpaTest (+2 cases). Backend test count delta meets the +46 plan target."
  - "Frontend tests: useRebalance.test.tsx (4 cases) + RebalanceCard.test.tsx (4 cases). Frontend test count 247 -> 255 (+8, meets target)."
  - "mvnw verify green; JaCoCo 60%/45% met; Spotless clean (auto-applied). Frontend lint --max-warnings 0 + tsc strict + Vitest + Vite build all clean."
  - "OpenAPI spec regen still defers per the pre-existing 26-01 OpenTelemetry sdk-autoconfigure ComponentLoader NoClassDefFoundError. The new endpoint surface is exercised end-to-end by RebalanceControllerWebMvcTest + SettingsControllerWebMvcTest."

rollback-boundary:
  - "git revert this plan's commits."
  - "Drop the V46 column via a fresh migration: ALTER TABLE user_settings DROP COLUMN rebalance_drift_threshold_percent; (Flyway Community has no undo)."
  - "Redis: rebalance:* keys auto-expire within 24h; no manual cleanup needed."
  - "No backfill required."
---

## Summary

Phase 28 sub-plan 02 ships the rebalance executor end-to-end: drift -> preview -> ticked-row commit -> materialised transactions -> account balance rolled. The owner who has set per-asset-type allocation targets on a portfolio now sees a "Rebalance" card on the portfolio detail page that lists per-holding BUY/SELL suggestions sized to bring `actual%` back inside a configurable drift tolerance, ticks the rows they want to apply, and clicks `Commit selected` -> the backend creates one `InvestmentTransaction` per ticked row in a single `@Transactional` shot, each emitting `InvestmentTransactionRecordedEvent` so the 25-01 holding projection + 27-03 `AccountBalanceListener` pick up automatically.

The implementation mirrors the 27-04 `BankCsvImportService` preview+commit pattern byte-for-byte: preview is `@Transactional(readOnly = true)`, returns a `RebalancePreview(proposalId, totalValueTry, accountCashTry, suggestions[], driftAfter, summaryWarnings, expiresAt)`, generates a server-side `proposalId` (UUID v4) stored in Redis with `30-minute TTL` keyed `rebalance:proposal:<userId>:<proposalId>` -> serialized canonical hash of the `(portfolioId, accountId, suggestions[])` set; commit (`POST .../commit`) takes `RebalanceCommitRequest(proposalId, accountId, selections[])`, recomputes the same canonical hash from the live current state, refuses when the cached `proposalId` is missing OR when the recomputed hash diverges from the cached hash (stale preview), refuses also when `proposalId` was already committed.

Suggestions are computed at the asset-type level (matching the existing `targetPercent` granularity on `portfolio_allocation_targets`) and then projected onto individual holdings within each asset-type bucket: for an OVERWEIGHT bucket, sell from the holdings within that bucket pro-rata to their current value share until the bucket is back to target; for an UNDERWEIGHT bucket, buy more of the highest-value existing holding in that bucket OR if the bucket has zero holdings flag a `NO_HOLDING_TO_BUY` warning per row. Cash availability check: when the SUM of suggested BUY amounts exceeds `account.currentBalance`, the executor proportionally scales DOWN every BUY suggestion by the `availableCash / requiredCash` ratio and surfaces a `CASH_PARTIAL_SCALEDOWN` warning. SELL suggestions are NEVER scaled because they REPLENISH cash. Equities (`STOCK` / `FUND`) are forced to integer quantities via `RoundingMode.DOWN`; crypto/gold/currency keep the asset's full `quantity_scale` (8).

The drift tolerance threshold is read from a NEW `user_settings.rebalance_drift_threshold_percent NUMERIC(5,2) NOT NULL DEFAULT 1.00` column added by Flyway `V46__user_settings_rebalance_drift_threshold.sql` (range `0.10..10.00` enforced by single-column CHECK; matches the V45 emergency-fund-targets pattern). Buckets whose `|driftPercent| <= threshold` are EXCLUDED from the suggestion list (no action needed). The threshold is configurable via `PUT /api/v1/settings/rebalance-threshold` mounted on the existing `SettingsController` (per the 28-01 fold-into-existing precedent).

Frontend ships a "Rebalance" sub-card on `PortfolioDetailPage` (NOT a separate route; mirrors `AllocationTargets` placement) gated behind `data.configured === true` from the existing allocation summary and `holdings.length > 0`. The card surfaces (a) a configurable drift-threshold slider hooked to the new settings field with a 400ms debounce, (b) the shared `AccountPicker`, (c) a `Generate suggestions` button that posts to `/preview` and renders the suggestions table inline, (d) per-row checkboxes (default checked for BUY/SELL with no warning, default unchecked for `NO_HOLDING_TO_BUY` / `QUANTITY_BELOW_LOT`), (e) a `Commit selected (N rows)` button that posts to `/commit` with the ticked indices and the cached `proposalId`. On commit success: invalidate `['portfolios', portfolioId, 'holdings']`, `['transactions', portfolioId]`, `['allocation', portfolioId]`, `['accounts']`, `['accounts','totals']`, show a success toast with the count of materialised transactions.

`docs/OPERATIONS.md` gains a new H2 `## Rebalancing a portfolio` covering the preview+commit workflow, the drift-threshold setting, the cash-scaling behaviour, the `NO_HOLDING_TO_BUY` warning interpretation, the integer-quantity quirk for STOCK/FUND, the proposal-expiry / stale-preview reasoning, and audit-log forensic queries. Phase 28 closes with this plan; ROADMAP table flips Phase 28 to `Complete (2/2 plans)`.

## Performance

- Backend test count: `1294 -> 1340` (+46, meets target).
- Frontend test count: `247 -> 255` (+8, meets target).
- `./mvnw -B -ntp clean verify -Pcoverage`: PASS, JaCoCo 60% / 45%, Spotless clean.
- `npm run lint -- --max-warnings 0`: PASS.
- `npm run typecheck`: PASS.
- `npm run test -- --run`: PASS.
- `npm run build`: PASS.

## Accomplishments

- V46 migration + UserSettings JPA field + three new audit constants (REBALANCE_PREVIEWED, REBALANCE_COMMITTED, USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED).
- RebalanceProposalStore Redis adapter with 30-minute open-proposal TTL + 24-hour committed-marker TTL.
- RebalanceService preview + commit orchestrator implementing the bucket-to-holding projection algorithm + cash scaling + integer-truncation + canonical-hash + audit emission.
- RebalanceController + four DTOs + WebMvc tests covering 200/400/404/409 paths.
- RebalanceConflictException + GlobalExceptionHandler 409 mapping for stale-preview + already-committed.
- SettingsService.updateRebalanceDriftThreshold + PUT /api/v1/settings/rebalance-threshold endpoint.
- Frontend RebalanceCard + useRebalance hook trio + types module + i18n namespace + PortfolioDetailPage mount.
- OPERATIONS.md `## Rebalancing a portfolio` operator runbook.

## Decisions

See the `key-decisions` block in the frontmatter.

## Deviations from Plan

None.

## Next steps

Phase 28 is complete (2/2 plans). Next operator action: cut the v1.4.0 release.
