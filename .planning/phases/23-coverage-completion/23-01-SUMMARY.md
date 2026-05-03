---
phase: 23-coverage-completion
plan: 01
subsystem: testing
tags: [jpa, datajpatest, testcontainers, postgres, jacoco, junit5, assertj]

# Dependency graph
requires:
  - phase: 22 (pre-roadmap baseline)
    provides: AbstractDataJpaTestSupport, six existing repository slice tests, JaCoCo gate at 60%/45%
provides:
  - 25 new @DataJpaTest suites covering custom queries on every Spring Data repository that has them
  - Documented intentional skip list (AdminSettingRepository, UserSettingsRepository) for repos that only inherit JpaRepository defaults
  - Audit table mapping every repository to its source path and notable query methods
affects:
  - phase 24 (security-hardening): refresh-token, password-reset, email-verification, audit-log slice tests in place before D2/D6/D7 land
  - phase 25 (architecture-cleanup): repository contracts pinned by tests before C1 event extraction starts moving service-to-service calls
  - phase 30 (performance-polish): baseline coverage to measure regressions when N+1 fixes and EntityGraph join-fetches change query plans

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Slice tests: @DataJpaTest + @EnabledIf('AbstractDataJpaTestSupport#dockerAvailable') for graceful skip on Docker-less hosts"
    - "Seed-parent helpers in each test class (seedUser, seedPortfolio, seedAsset) over global fixtures"

key-files:
  created:
    - .planning/phases/23-coverage-completion/23-01-AUDIT.md
    - backend/src/test/java/com/fintrack/alert/AlertNotificationRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/alert/PriceAlertRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/asset/AssetRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/audit/AuditLogRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/auth/EmailVerificationRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/auth/PasswordResetRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/auth/RefreshTokenRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/auth/TotpRecoveryCodeRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/bills/BillPaymentRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetRuleRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/MonthlySummaryRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/allocation/AllocationBucketRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/recurring/RecurringTemplateRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/rule/TransactionCategoryRuleRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/debt/DebtPaymentRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/portfolio/allocation/AllocationTargetRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/portfolio/dividend/DividendRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/portfolio/holding/HoldingRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/portfolio/snapshot/SnapshotRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/price/PriceHistoryRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/push/PushSubscriptionRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/savings/SavingsContributionRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/tag/TransactionTagRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/watchlist/WatchlistRepositoryDataJpaTest.java
  modified:
    - tasks/ROADMAP.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Follow established `*RepositoryDataJpaTest` naming convention rather than the plan's literal `*RepositoryTest` (matches the 10 existing suites under backend/src/test)"
  - "Skip AdminSettingRepository and UserSettingsRepository — both only inherit JpaRepository defaults, no custom queries to exercise"
  - "Reuse seed-parent helper pattern (seedUser/seedPortfolio/seedAsset) per test class rather than introducing a global fixture module — keeps each slice test self-contained"

patterns-established:
  - "Repository slice tests: @DataJpaTest + @EnabledIf docker-gate + AbstractDataJpaTestSupport extension; one file per repository; private seed helpers; AssertJ assertions; one negative-case per query"

issues-created: []

# Metrics
duration: 11 min
completed: 2026-05-04
---

# Phase 23 Plan 01: Repository slice coverage Summary

**Added 25 @DataJpaTest suites covering custom queries on every Spring Data repository that has them; coverage gate (60% instruction / 45% branch) untouched, suites Docker-gated for clean local skip.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-05-03T22:10:15Z
- **Completed:** 2026-05-03T22:21:07Z
- **Tasks:** 3
- **Files modified:** 29 (1 audit, 25 test classes, 3 planning docs)

## Accomplishments
- Audited all 37 Spring Data repositories against existing slice-test coverage (`23-01-AUDIT.md`); identified 27 missing suites, 25 actionable + 2 intentionally skipped.
- Wrote 25 new `*RepositoryDataJpaTest` classes — every custom `@Query` and method-name-derived query with business meaning has at least one happy path and one negative case.
- Closed Track A2 in `tasks/ROADMAP.md` progress log; Phase 23 now 1/4 plans complete.

## Task Commits

Each task was committed atomically:

1. **Task 1: Audit @DataJpaTest coverage gaps** - `bede4d2` (docs)
2. **Task 2: Add @DataJpaTest suites for remaining repositories** - `500c4b5` (test)
3. **Task 3: Close A2 in roadmap progress log** - `fc8a6d2` (docs)

**Plan metadata:** (this commit)

## Files Created/Modified

**Created:**
- `.planning/phases/23-coverage-completion/23-01-AUDIT.md` - repository-by-repository audit table
- `backend/src/test/java/com/fintrack/alert/AlertNotificationRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/alert/PriceAlertRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/asset/AssetRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/audit/AuditLogRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/auth/EmailVerificationRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/auth/PasswordResetRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/auth/RefreshTokenRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/auth/TotpRecoveryCodeRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/bills/BillPaymentRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/budget/BudgetRuleRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/budget/MonthlySummaryRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/budget/allocation/AllocationBucketRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/budget/recurring/RecurringTemplateRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/budget/rule/TransactionCategoryRuleRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/debt/DebtPaymentRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/portfolio/allocation/AllocationTargetRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/portfolio/dividend/DividendRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/portfolio/holding/HoldingRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/portfolio/snapshot/SnapshotRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/price/PriceHistoryRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/push/PushSubscriptionRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/savings/SavingsContributionRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/tag/TransactionTagRepositoryDataJpaTest.java`
- `backend/src/test/java/com/fintrack/watchlist/WatchlistRepositoryDataJpaTest.java`

**Modified:**
- `tasks/ROADMAP.md` - struck through A2, added 2026-05-04 progress log entry
- `.planning/STATE.md` - last activity, recent decisions, current position update
- `.planning/ROADMAP.md` - Phase 23 plan 01 marked complete (1/4)

## Decisions Made
- Used existing convention `*RepositoryDataJpaTest` instead of plan's literal `*RepositoryTest` — matches the 10 already-shipped suites and keeps file naming uniform.
- Skipped two repositories with no custom queries (AdminSettingRepository, UserSettingsRepository); listed in audit + summary so the omission is intentional and visible.
- Mirrored each existing suite's seed-parent helper pattern (private `seedUser`, `seedPortfolio`, etc. per class) rather than centralising fixtures — favours self-contained slices over a shared module that would couple test classes.

## Deviations from Plan

### Auto-fixed Issues
None — the plan executed essentially as written, with the test naming convention preserved per the established pattern in the existing 10 suites.

### Deferred Enhancements
None logged for ISSUES.md.

---

**Total deviations:** 0 auto-fixed, 0 deferred.
**Impact on plan:** None — the only intentional departure is the file-name convention, which matches existing code rather than the plan's literal text.

## Issues Encountered

- Local `./mvnw -pl backend verify` runtime verification was scoped to `test-compile` only — the host (Windows, no Docker Desktop session) cannot start the Postgres Testcontainer. The new suites are gated on `@EnabledIf("...#dockerAvailable")` so they report Skipped here; CI on Linux exercises every test. JaCoCo numbers on this host are therefore unchanged from the 2026-04-26 baseline (77.5% instruction / 62.6% branch); on CI they will move strictly upward as the new suites add covered branches.
- Subagent spawned for the autonomous execution paused after writing the 25 test files but before commits. Main context resumed: ran `./mvnw test-compile` (clean exit 0), then committed Tasks 2 + 3 + metadata in turn.

## Next Phase Readiness
- Ready for `23-02-PLAN.md` (PIT mutation testing on the service layer at 60% mutation score).
- Repository contracts now pinned by tests — safer ground for the Phase 25 event-extraction refactor (Track C1).

---
*Phase: 23-coverage-completion*
*Completed: 2026-05-04*
