# Deferred Issues

Each entry is a non-blocking improvement surfaced during plan execution. Items here are deliberately
not fixed in the originating plan because they exceed its scope or would require an architectural
change. They are revisited via `/gsd:consider-issues` between phases.

## Open

### Mutation-coverage lift backlog (Phase 23-02)

Each ISS-1xx item below corresponds to a service whose mutation kill rate is currently under the 60%
gate captured in `.planning/phases/23-coverage-completion/23-02-BASELINE.md`. The project-level gate
already passes at 63%; these items track the per-class lift work that did not fit the 23-02 envelope.

- **ISS-100** — `com.fintrack.report.ReportService` mutation kill: 16% (27/169). 142 surviving
  mutations, mostly `VoidMethodCall` on PDF/XLSX builder calls and `NullReturnVals` on the section
  helpers. Warrants its own dedicated plan; tests need to assert on built artefacts (rendered PDF /
  XLSX bytes or extracted text) rather than mocking the apache-poi / openpdf builder API.

- **ISS-101** — `com.fintrack.auth.AuthService` mutation kill: 28% (23/81). 58 surviving mutations
  on registration, login, and refresh paths. Existing tests verify happy paths but rarely assert
  branching (e.g., active-account guard, locked-account flag). Lift via dedicated plan with branch
  flips on every guard.

- **ISS-102** — `com.fintrack.budget.BudgetService` mutation kill: 22% (22/98). 76 surviving
  mutations on date-range guards and monthly aggregations. Service is large and method-heavy; lift
  in a dedicated plan with parametrised tests across month boundaries.

- **ISS-103** — `com.fintrack.price.PriceSyncService` mutation kill: 53% (81/154). 73 surviving
  mutations on cache writes and stale-check branches. The class also carries the `WebClient.block()`
  + `Thread.sleep` smell flagged by `.planning/codebase/CONCERNS.md` for Phase 25; mutation lift
  should land alongside that refactor for maximum leverage.

- **ISS-104** — `com.fintrack.debt.DebtService` mutation kill: 57% (41/72). 31 surviving mutations
  on amortisation math and payoff guards. Tractable lift: add property-style tests around `MathMutator`
  survivors for the amortisation formula.

- **ISS-105** — `com.fintrack.backup.BackupService` mutation kill: 34% (20/58). 38 surviving
  mutations. Requires production refactor first: filesystem and `Process` calls are constructed
  inline; need to extract a small `BackupExecutor` collaborator that can be mocked.

- **ISS-106** — `com.fintrack.push.PushService` mutation kill: 39% (11/28). 17 surviving mutations
  inside `send()`. The class field-initialises `WebClient` (`new WebClient.builder().build()`); needs
  constructor injection of `WebClient.Builder` so tests can stub the chain.

- **ISS-107** — `com.fintrack.notification.MailService` mutation kill: 44% (7/16). 9 surviving
  mutations on `JavaMailSender` interactions. Mailer is currently injected, but the
  `MimeMessageHelper` is `new`-ed inside `send()` blocking mock-based assertion of headers / body.
  Refactor to a small templating collaborator, then add tests.

- **ISS-108** — `com.fintrack.portfolio.dividend.DividendService` mutation kill: 47% (9/19). 10
  surviving mutations on the currency-equals branch and FX conversion call. Tractable lift in its
  own small plan: add tests for non-pivot currencies, null withholding tax, and listForAsset cross-
  portfolio filtering.

- **ISS-109** — `com.fintrack.budget.allocation.CashFlowAllocatorService` mutation kill: 58% (7/12).
  5 surviving `MathMutator` and `ConditionalsBoundary` mutations on percent rounding. Smallest lift
  in the backlog; ~3 targeted tests around half-up rounding boundaries should clear it.

## Closed

(none)
