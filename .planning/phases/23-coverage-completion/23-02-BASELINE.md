# Phase 23 Plan 02: Mutation Testing Baseline

**Captured:** 2026-05-04
**Tool:** PIT (pitest-maven 1.17.4) with pitest-junit5-plugin 1.2.1
**Profile:** `mutation` (opt-in; not part of default `verify` lifecycle)
**Run command:** `cd backend && ./mvnw -P mutation org.pitest:pitest-maven:mutationCoverage`

## Project Total

| Metric | Value |
|--------|-------|
| Classes scanned | 44 (services + a few test-discovered helpers) |
| Line coverage of mutated classes | 87% (3803/4378) |
| **Mutation kill** | **63% (1039/1659)** |
| Test strength | 74% (1039/1404) |
| Project-level gate | 60% — **PASSING** |

The configured `mutationThreshold=60` is project-level; PIT does not natively enforce it per class. Per-class scores below are the lift backlog.

## Per-Package Summary

| Package | Classes | Mutation Kill | Status |
|---------|---------|---------------|--------|
| com.fintrack.alert | 2 | 94% (30/32) | green |
| com.fintrack.analytics | 1 | 96% (27/28) | green |
| com.fintrack.analytics.benchmark | 1 | 75% (9/12) | green |
| com.fintrack.asset | 1 | 96% (22/23) | green |
| com.fintrack.audit | 1 | 75% (9/12) | green |
| com.fintrack.auth | 7 | 61% (148/242) | borderline (one bad apple: AuthService) |
| com.fintrack.backup | 1 | 34% (20/58) | **below 60%** |
| com.fintrack.bills | 1 | 68% (30/44) | green |
| com.fintrack.budget | 3 | 44% (64/146) | **below 60%** (BudgetService is the offender) |
| com.fintrack.budget.allocation | 1 | 58% (7/12) | **below 60%** (close) |
| com.fintrack.budget.receipt | 1 | 74% (25/34) | green |
| com.fintrack.budget.recurring | 1 | 91% (30/33) | green |
| com.fintrack.budget.rule | 1 | 86% (25/29) | green |
| com.fintrack.dashboard | 1 | 100% (21/21) | green |
| com.fintrack.debt | 1 | 57% (41/72) | **below 60%** (close) |
| com.fintrack.fire | 1 | 76% (54/71) | green |
| com.fintrack.imports | 1 | 87% (58/67) | green |
| com.fintrack.networth | 1 | 94% (16/17) | green |
| com.fintrack.notification | 1 | 44% (7/16) | **below 60%** |
| com.fintrack.portfolio | 1 | 86% (12/14) | green |
| com.fintrack.portfolio.allocation | 1 | 85% (22/26) | green |
| com.fintrack.portfolio.dividend | 1 | 47% (9/19) | **below 60%** |
| com.fintrack.portfolio.holding | 1 | 86% (18/21) | green |
| com.fintrack.portfolio.risk | 1 | 83% (33/40) | green |
| com.fintrack.portfolio.snapshot | 1 | 95% (18/19) | green |
| com.fintrack.portfolio.transaction | 1 | 77% (27/35) | green |
| com.fintrack.price | 2 | 57% (97/170) | **below 60%** (PriceSyncService is the offender) |
| com.fintrack.push | 1 | 39% (11/28) | **below 60%** |
| com.fintrack.report | 1 | 16% (27/169) | **below 60%** (ReportService) |
| com.fintrack.report.capitalgains | 1 | 62% (21/34) | green (borderline) |
| com.fintrack.savings | 1 | 77% (43/56) | green |
| com.fintrack.settings | 1 | 100% (17/17) | green |
| com.fintrack.tag | 1 | 100% (36/36) | green |
| com.fintrack.watchlist | 1 | 83% (5/6) | green |

## Per-Class Lift Backlog (below 60%)

| Class | Kill % | Surviving | Dominant surviving mutators | Lift status |
|-------|--------|-----------|-----------------------------|-------------|
| `com.fintrack.report.ReportService` | 16% (27/169) | 142 | `VoidMethodCall`, `NullReturnVals`, `EmptyObjectReturnVals` on PDF/XLSX builder calls | DEFERRED — ISS-100 |
| `com.fintrack.auth.AuthService` | 28% (23/81) | 58 | `NegateConditionals`, `VoidMethodCall` on registration/login branches | DEFERRED — ISS-101 |
| `com.fintrack.budget.BudgetService` | 22% (22/98) | 76 | branch flips on date-range guards; arithmetic on monthly aggregations | DEFERRED — ISS-102 |
| `com.fintrack.price.PriceSyncService` | 53% (81/154) | 73 | `VoidMethodCall` on cache writes; `NegateConditionals` on stale-check branches | DEFERRED — ISS-103 |
| `com.fintrack.debt.DebtService` | 57% (41/72) | 31 | `MathMutator` on amortization math; `NegateConditionals` on payoff guards | DEFERRED — ISS-104 |
| `com.fintrack.backup.BackupService` | 34% (20/58) | 38 | filesystem and Process side-effects; needs constructor injection of fs writer to test | DEFERRED — ISS-105 (refactor required) |
| `com.fintrack.push.PushService` | 39% (11/28) | 17 | `WebClient` chain inside `send()`; field-initialised client cannot be mocked | DEFERRED — ISS-106 (refactor required) |
| `com.fintrack.notification.MailService` | 44% (7/16) | 9 | `JavaMailSender` interactions; no constructor injection point in the current shape | DEFERRED — ISS-107 (refactor required) |
| `com.fintrack.portfolio.dividend.DividendService` | 47% (9/19) | 10 | `NegateConditionals` on currency-equals branch; `VoidMethodCall` on FX conversion | DEFERRED — ISS-108 |
| `com.fintrack.budget.allocation.CashFlowAllocatorService` | 58% (7/12) | 5 | `MathMutator` and `ConditionalsBoundary` on percent rounding | DEFERRED — ISS-109 |
| `com.fintrack.auth.FinTrackUserDetailsService` | 0% (0/4) | 4 | no test existed | LIFTED in this plan (see commit) |

## Lifted in this plan

- `com.fintrack.auth.FinTrackUserDetailsService`: 0% → expected ~100% after the new `FinTrackUserDetailsServiceTest` covers both `loadUserByUsername` and `loadUserByUserId` happy paths, the missing-user paths, and the malformed-UUID input.

## Deferred Enhancements (logged in `.planning/ISSUES.md`)

The remaining 10 below-threshold classes are tracked as ISS-100 through ISS-109. Each is its own scoped follow-up because the test additions per class range from "moderate" (DividendService, CashFlowAllocatorService, DebtService, BudgetService) to "needs production refactor first" (BackupService, PushService, MailService — these inject collaborators by `new`-ing them inside the class, so they cannot be mocked without constructor injection). ReportService alone has 142 surviving mutations and warrants its own dedicated plan.

## Equivalent Mutations

None muted in this baseline. No mutators are excluded; the default `DEFAULTS` mutator set was used.

## Reproduce

```
cd backend
./mvnw -P mutation org.pitest:pitest-maven:mutationCoverage
# Open target/pit-reports/index.html
```

To scope a per-class run for fast iteration:

```
cd backend
./mvnw -P mutation org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=com.fintrack.report.ReportService \
  -DtargetTests=com.fintrack.report.ReportServiceTest \
  -DmutationThreshold=0
```
