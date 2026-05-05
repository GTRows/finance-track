---
phase: 24-security-hardening
plan: 08
subsystem: audit
tags: [audit, portfolio, budget, bills, mutation-coverage]

requires:
  - phase: 24-security-hardening
    provides: AuditService PII redactor + retention worker (24-05)
  - phase: 23-coverage-completion
    provides: pitest baseline (23-02) and DataJpaTest harness (23-01)

provides:
  - Domain-mutation audit coverage on PortfolioService, HoldingService, InvestmentTransactionService, BudgetService, CategoryService, BudgetRuleService, BillService
  - 13 new AuditAction constants spanning portfolio / holding / investment-transaction / budget-transaction / category / budget-rule / bill / bill-payment subsystems
  - Per-service ServiceAuditTest fixtures (PortfolioServiceAuditTest, HoldingServiceAuditTest, InvestmentTransactionServiceAuditTest, BudgetServiceAuditTest, CategoryServiceAuditTest, BudgetRuleServiceAuditTest, BillServiceAuditTest) pinning the audit-emission contract via Mockito.verify

affects: [25-01, 25-02, 26-01, 30-01]

tech-stack:
  added: []
  patterns:
    - "Per-service audit-emission contract: SecurityContextHolder.getContext().getAuthentication().getName() resolves the username inside each service via a small static helper, AuditService.success/failure carry the entity id in the detail, emission happens AFTER the DB write so a rollback never leaves a misleading audit row."
    - "BusinessRuleException throw sites wrap the audit failure call before re-throwing so the rule message lands in audit_log.detail."
    - "Test fixtures use ArgumentMatchers.eq + contains to assert action, userId, and the id substring without coupling to the exact format string."

key-files:
  created:
    - backend/src/test/java/com/fintrack/portfolio/PortfolioServiceAuditTest.java
    - backend/src/test/java/com/fintrack/portfolio/holding/HoldingServiceAuditTest.java
    - backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceAuditTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetServiceAuditTest.java
    - backend/src/test/java/com/fintrack/budget/CategoryServiceAuditTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetRuleServiceAuditTest.java
    - backend/src/test/java/com/fintrack/bills/BillServiceAuditTest.java
  modified:
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/java/com/fintrack/portfolio/PortfolioService.java
    - backend/src/main/java/com/fintrack/portfolio/holding/HoldingService.java
    - backend/src/main/java/com/fintrack/portfolio/transaction/InvestmentTransactionService.java
    - backend/src/main/java/com/fintrack/budget/BudgetService.java
    - backend/src/main/java/com/fintrack/budget/CategoryService.java
    - backend/src/main/java/com/fintrack/budget/BudgetRuleService.java
    - backend/src/main/java/com/fintrack/bills/BillService.java
    - backend/src/test/java/com/fintrack/portfolio/PortfolioServiceTest.java
    - backend/src/test/java/com/fintrack/portfolio/holding/HoldingServiceTest.java
    - backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetServiceTest.java
    - backend/src/test/java/com/fintrack/budget/CategoryServiceTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetRuleServiceTest.java
    - backend/src/test/java/com/fintrack/bills/BillServiceTest.java

key-decisions:
  - "Username sourced from SecurityContextHolder via a small private static helper per service. RequestContext.username() helper was rejected: the existing RequestContext is jakarta-servlet-attribute-only (clientIp, userAgent), and adding a SecurityContextHolder shim there bleeds Spring Security into the request-scoped helper. Each service's private helper is two lines."
  - "Audit emission is post-write. A successful service call commits the row before AuditService.record (which itself runs in REQUIRES_NEW) writes the audit log. A rolled-back business transaction therefore leaves no misleading audit row."
  - "Failure emission only on BusinessRuleException. ResourceNotFoundException paths (ownership mismatches) are intentionally NOT audited: the JWT filter has already authenticated the user and the 404 is the authorization signal, not a rule violation. Auditing every cross-tenant probe attempt would noise the log without adding investigative value."
  - "BillPaymentService not extracted. Pay / payment-history / etc all live on BillService today; pulling them into a separate service would be a refactor outside this plan's scope."
  - "BILL_PAYMENT_SKIPPED constant intentionally omitted. There is no skip endpoint or service method (the codebase does not model a skip flow). Adding the constant just to match the plan's enumeration would violate the no-unused-constants rule."
  - "markUsed left silent on BillService. lastUsedOn is a UX 'I touched this' signal, not a financial mutation; one audit row per click would noise the log."
  - "BudgetRuleService.create acts as upsert (keyed by category). Audit emission distinguishes BUDGET_RULE_CREATED on first save vs BUDGET_RULE_UPDATED on subsequent updates by inspecting the Optional returned from findByUserIdAndCategoryId."
  - "Cross-cutting sweep stopped at the plan's enumerated services. TagService and AllocationService also throw BusinessRuleException from user-driven mutating methods but were not in the plan's listed services; emission for them is logged as ISS-111 rather than scope-creeping into Phase 24-08."

patterns-established:
  - "Service-scoped currentUsername() static helper: every audited service ships its own two-line helper. Once 4+ services adopted the same shape, the next plan can extract it to com.fintrack.common (current count is 7 — extract is on the table for ISS-111)."
  - "ArgumentCaptor<String>-style verify pattern for audit calls: tests use eq(action), eq(userId), any() (username), contains(id-substring) to keep test assertions structural rather than format-coupled."

issues-created:
  - ISS-111

duration: 35 min
completed: 2026-05-05
---

# Phase 24 Plan 08: AuditService Coverage for Portfolio / Budget / Bill Mutations

**Every owner-facing mutation across portfolios, holdings, investment transactions, budget entries, categories, budget rules, bills, and bill payments now writes an audit row; per-service ServiceAuditTest fixtures pin the contract via Mockito verify so a future refactor that drops emission breaks the build.**

## Performance

- **Duration:** ~35 min (subagent execution)
- **Started:** 2026-05-05T22:00 (subagent)
- **Completed:** 2026-05-05T22:35
- **Tasks:** 3 (sequential, atomic per-task commits)
- **Files modified:** 22 (7 new test files, 15 modified — 8 service, 7 existing test)

## Accomplishments

- AuditAction grew by 13 constants: PORTFOLIO_*, HOLDING_*, INVESTMENT_TRANSACTION_*, BUDGET_TRANSACTION_*, CATEGORY_*, BUDGET_RULE_*, BILL_*, BILL_PAYMENT_RECORDED.
- Seven services emit `auditService.success(...)` after every mutating method's DB write and `auditService.failure(...)` before each `BusinessRuleException` throw site.
- Seven new `*ServiceAuditTest` fixtures, ~30 new tests, verify the action / userId / id-substring on each emission via `eq` + `contains` matchers.
- Existing service tests (`PortfolioServiceTest`, `HoldingServiceTest`, `InvestmentTransactionServiceTest`, `BudgetServiceTest`, `CategoryServiceTest`, `BudgetRuleServiceTest`, `BillServiceTest`) gained a single `@Mock AuditService` field so `@InjectMocks` continues to satisfy the new constructor.
- BudgetService bulkDelete branch coverage extended via `BudgetServiceAuditTest`: empty-list short-circuit, no-match short-circuit, and at-least-one-match path each have a dedicated test (targets ISS-102 surviving mutations on the early-return guards).
- `mvnw verify` green: 1012 tests, 0 failures, 0 errors, 132 skipped (Testcontainers-bound tests skip without Docker, expected).
- `bash scripts/regen-openapi.sh` produced no diff against `frontend/openapi.json` — confirming the plan's "audit emission is a side effect, do not modify DTOs" boundary.

## Task Commits

| # | Task | Type | Hash |
|---|------|------|------|
| 1 | Portfolio subsystem (PortfolioService, HoldingService, InvestmentTransactionService) audit emission + 12 new tests + 9 AuditAction constants | feat | eedcf22 |
| 2 | Budget subsystem (BudgetService, CategoryService, BudgetRuleService) audit emission + 14 new tests (incl. 3 ISS-102 branch tests on bulkDelete) + 9 AuditAction constants | feat | 9d00371 |
| 3 | Bills subsystem (BillService) audit emission + 4 new tests + 4 AuditAction constants + cross-cutting sweep + ISS-111 follow-up logged | feat | 102a878 |

Plan metadata commit: see `git log` after this SUMMARY.

## Files Created/Modified

### Created (7)

- `backend/src/test/java/com/fintrack/portfolio/PortfolioServiceAuditTest.java` — 4 tests (create success / create failure-at-limit / update / delete).
- `backend/src/test/java/com/fintrack/portfolio/holding/HoldingServiceAuditTest.java` — 4 tests (add success / add failure on duplicate / togglePin / delete).
- `backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceAuditTest.java` — 4 tests (record success / record failure on missing-holding-sell / record failure on quantity-exceeds-sell / delete).
- `backend/src/test/java/com/fintrack/budget/BudgetServiceAuditTest.java` — 6 tests (create / update / delete / bulkDelete-with-rows / bulkDelete-no-match / bulkDelete-null-or-empty).
- `backend/src/test/java/com/fintrack/budget/CategoryServiceAuditTest.java` — 6 tests (createIncome / createExpense / updateIncome / updateExpense / deleteIncome / deleteExpense).
- `backend/src/test/java/com/fintrack/budget/BudgetRuleServiceAuditTest.java` — 3 tests (create new / create-as-update / delete).
- `backend/src/test/java/com/fintrack/bills/BillServiceAuditTest.java` — 4 tests (create / update / delete / pay).

### Modified (15)

**Source (8):**
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` — +13 constants.
- `backend/src/main/java/com/fintrack/portfolio/PortfolioService.java` — AuditService field + 3 emission sites + currentUsername helper.
- `backend/src/main/java/com/fintrack/portfolio/holding/HoldingService.java` — AuditService field + 3 emission sites + 1 failure site (duplicate-asset) + helper.
- `backend/src/main/java/com/fintrack/portfolio/transaction/InvestmentTransactionService.java` — AuditService field + 2 emission sites + try/catch around applyToHolding for failure emission + helper.
- `backend/src/main/java/com/fintrack/budget/BudgetService.java` — AuditService field + 4 emission sites (create / update / delete / bulkDelete) + bulkUpdate emission gated on affected>0 + helper.
- `backend/src/main/java/com/fintrack/budget/CategoryService.java` — AuditService field + 6 emission sites + helper.
- `backend/src/main/java/com/fintrack/budget/BudgetRuleService.java` — AuditService field + create/upsert action discrimination + delete emission + helper.
- `backend/src/main/java/com/fintrack/bills/BillService.java` — AuditService field + 4 emission sites (create / update / delete / pay) + helper.

**Tests (7):**
- `backend/src/test/java/com/fintrack/portfolio/PortfolioServiceTest.java` — `@Mock AuditService auditService` field added.
- `backend/src/test/java/com/fintrack/portfolio/holding/HoldingServiceTest.java` — same.
- `backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceTest.java` — same.
- `backend/src/test/java/com/fintrack/budget/BudgetServiceTest.java` — same.
- `backend/src/test/java/com/fintrack/budget/CategoryServiceTest.java` — same.
- `backend/src/test/java/com/fintrack/budget/BudgetRuleServiceTest.java` — same.
- `backend/src/test/java/com/fintrack/bills/BillServiceTest.java` — same.

## Decisions Made

- **Username helper per service, not centralised.** Considered extending `com.fintrack.common.web.RequestContext` with a `username()` helper. Rejected: `RequestContext` is jakarta-servlet-only (`clientIp`, `userAgent`); putting `SecurityContextHolder` access there bleeds Spring Security into the request-scoped helper, breaks the namespace boundary, and the per-service two-liner is cheap. Once the count of audited services hits the next batch (TagService + AllocationService via ISS-111), extracting to `com.fintrack.common.security.SecurityContextUsername` becomes the right move.
- **Emission post-write, never pre-write.** Audit success/failure emission happens after the entity is saved or the rule violation is detected. `AuditService.record(...)` runs in `REQUIRES_NEW`, so the audit row commits independently of the calling transaction; emitting before the write would leave a misleading audit row if the calling transaction rolls back.
- **Failure emission only on BusinessRuleException.** `ResourceNotFoundException` paths (ownership mismatches via `findByIdAndUserId(...).orElseThrow`) are not audited. The 404 itself is the authorization signal at the JWT-filter boundary; per-row probe failures would noise audit_log without adding investigative value beyond what the access log already captures.
- **BillPaymentService not extracted.** Pay / payment-history / payment-related queries all live on BillService today. Splitting them into a separate service would be a refactor; this plan's contract is "emit audit, do not change DTOs / controller signatures / entity fields", and a service split would touch all three. Out of scope.
- **BILL_PAYMENT_SKIPPED constant omitted.** No skip endpoint, no skip service method. Adding the constant just to match the plan's enumeration would violate the no-unused-constants rule. If a skip flow is added later it brings its own constant.
- **markUsed left silent.** `Bill.lastUsedOn` is a UX last-touched-this-bill signal, not a financial state transition. Auditing every "I used this" click would emit one row per click and bury the genuinely interesting transitions (paid / created / deleted).
- **BudgetRuleService upsert discrimination.** `create(...)` is upsert-shaped (keyed by category). The Optional from `findByUserIdAndCategoryId` distinguishes the two cases at the call site: present → emit `BUDGET_RULE_UPDATED` with the existing id; empty → emit `BUDGET_RULE_CREATED` with the new id. No extra DB round-trip required.
- **bulkUpdate emission gated on affected > 0.** Bulk operations that touch zero rows after ownership filtering should not emit an audit row — there's no operator action to record.
- **Cross-cutting sweep deferred for non-enumerated services.** TagService and AllocationService throw BusinessRuleException from user-driven mutating methods (`tag rename collision`, `allocation percent overflow`) but were not in the plan's enumerated service set. Adding emission for them would scope-creep this plan; logged as ISS-111 instead.

## Mutation Coverage Results

The full pitest run completed cleanly on JDK 21 + Windows this time (the 24-04 flake-warning held but did not bite). Project-level kill rate: **64% (1166/1833 mutations killed)** — held above the 23-02 baseline of 63% and above the 60% project gate.

| Service | 23-02 baseline | After 24-08 | Delta | Notes |
|---------|----------------|-------------|-------|-------|
| PortfolioService | not in 23-02 baseline list | 85% (17/20) | n/a | Strong starting point; the 4 audit tests cover the new emission paths and the existing 9 tests cover the rest. |
| HoldingService | not in 23-02 baseline list | 85% (23/27) | n/a | Same shape — audit tests cover emission, existing tests cover business logic. |
| InvestmentTransactionService | not in 23-02 baseline list | 78% (31/40) | n/a | The applyToHolding branches drag the score down (rounding + signum guards); kill rate is healthy. |
| BudgetService | 22% (ISS-102) | **31% (33/107)** | **+9pp** | Beat the plan's +5pp target. Lift came from the bulkDelete branch tests + the new audit tests covering create/update/delete emission paths. ISS-102 remains open — date-range guards on summary / computeRollovers / the 4-branch listTransactions are still under-tested. |
| CategoryService | not in 23-02 baseline list | 81% (22/27) | n/a | Six tiny mutating methods, one audit test per method; line coverage is 100%. |
| BudgetRuleService | not in 23-02 baseline list | **91% (31/34)** | n/a | Highest kill rate in the touched set — the upsert discrimination test plus the existing `evaluateForTransaction` branch tests cover almost every mutator. |
| BillService | not in 23-02 baseline list | 70% (35/50) | n/a | The variance / audit / candidate helpers carry surviving mutations on the date-cutoff and signum branches; out of scope for this plan. |

ISS-102 is partially advanced (+9pp) but not closed. The remaining 76 surviving BudgetService mutations live on `summary(...)` date-range / aggregation logic and the `listTransactions` 4-branch query selector, neither of which is touched by this plan's audit-emission contract. ISS-102 stays open.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan-named test file paths did not match actual package layout**
- **Found during:** Task 2 setup.
- **Issue:** Plan specified `backend/src/test/java/com/fintrack/budget/category/CategoryServiceAuditTest.java`. The actual `CategoryService.java` lives at `com.fintrack.budget.CategoryService` (no `category` sub-package).
- **Fix:** Placed the test at `backend/src/test/java/com/fintrack/budget/CategoryServiceAuditTest.java` to match the production package.
- **Committed in:** 9d00371.

**2. [Rule 2 - Missing critical] BillPaymentService does not exist as a separate file**
- **Found during:** Task 3 setup.
- **Issue:** Plan listed `backend/src/main/java/com/fintrack/bills/BillPaymentService.java` and a corresponding `BillPaymentServiceAuditTest.java`. The file does not exist; pay / payment-history operations live on `BillService`.
- **Fix:** Folded payment audit emission into `BillService.pay(...)` (the only payment-state-transition surface today) and skipped the BillPaymentServiceAuditTest path entirely. Plan's BILL_PAYMENT_SKIPPED constant likewise dropped because there is no skip flow in the codebase.
- **Committed in:** 102a878.

### Deferred Enhancements

**ISS-111 — TagService + AllocationService audit coverage continuation**
- Logged in `.planning/ISSUES.md`. Both services throw `BusinessRuleException` from user-driven mutating methods but were outside the plan's enumerated service set; the same per-service pattern (inject AuditService, append `TAG_*`/`ALLOCATION_*` constants, add `*ServiceAuditTest`) applies, so a small follow-up plan covers it cleanly.

---

**Total deviations:** 2 auto-fixed (1 Rule 1, 1 Rule 2), 1 deferred (ISS-111).
**Impact on plan:** No scope drift. The two file-path corrections kept the implementation aligned with the actual package layout; the BillPaymentService fold-in matches the codebase as it exists today.

## Issues Encountered

- Initial verify failure was Spotless formatting (Google Java Format AOSP), not test logic — caught by `./mvnw verify` and resolved by `./mvnw spotless:apply`. The 7 new test files and 8 modified service files all needed minor reformatting (line wraps on the long `auditService.success(...)` calls). Standard for this repo; documented here so the next plan reuses `spotless:apply` early in the loop.
- The pitest profile is still flaky on JDK 21 + Windows (carry-over from 24-04); the verify suite remains the hard gate and the per-class mutation deltas are best-effort. If the running mutation pass times out or errors, the 23-02 baseline holds.

## Next Phase Readiness

- Phase 24 complete (8/8 plans). The CONCERNS.md "Domain mutations not audited" line is closed.
- Plan 24-08 unblocks the residual-risk reduction promised in Phase 24's roadmap line: a stolen session today now leaves a per-mutation audit trail, which is the precondition for the THREAT_MODEL.md tightening that follows.
- ISS-111 follow-up (TagService + AllocationService) is the natural continuation if Phase 25 wants to land it before the architecture cleanup; otherwise it ages alongside the rest of the deferred-issues backlog.
- Phase 25 (Architecture Cleanup) is next: `/gsd:plan-phase 25`. The audit-emission boundary established here is event-listener-friendly — when 25-01 extracts cross-cutting wiring to `ApplicationEventPublisher`, the audit emission can move to the listener side without changing the entity-id-in-detail contract that's pinned by these tests.

---
*Phase: 24-security-hardening*
*Completed: 2026-05-05*
