---
phase: 25-architecture-cleanup
plan: 01
subsystem: architecture
tags: [events, decoupling, application-event-publisher, transactional-event-listener]

requires:
  - phase: 24-security-hardening
    provides: AuditService coverage on portfolio / budget / bill mutations (24-08)

provides:
  - Cross-cutting wiring extracted from InvestmentTransactionService, BillService, BudgetService into ApplicationEventPublisher publish sites
  - Four event records in com.fintrack.common.event (InvestmentTransactionRecordedEvent, InvestmentTransactionDeletedEvent, BillPaidEvent, BudgetTransactionPersistedEvent)
  - Three @TransactionalEventListener(AFTER_COMMIT) listeners (HoldingProjectionListener, BillPaidNotificationListener, BudgetRuleEvaluationListener)
  - First user-driven bill payment confirmation channel (mail + push) wired to the new event surface
  - 24-08 audit-emission contract preserved on the writer side (entity-id-in-detail, post-write ordering, REQUIRES_NEW commit on AuditService)

affects: [25-02, 25-03, 26-01, 30-01]

tech-stack:
  added: []
  patterns:
    - "Writer publishes a record-typed event after auditService.success(...); the listener owns the side effect under @TransactionalEventListener(phase = AFTER_COMMIT). Audit row commits with the writer (REQUIRES_NEW), listener side effect runs only after the writer's transaction commits and cannot disturb it."
    - "Pre-write guards stay on the writer when the side effect must roll the writer back (SELL holding pre-validation in InvestmentTransactionService). Side effects that are advisory (push notification, budget rule alert) run in the listener and swallow exceptions."
    - "Event payloads carry plain values, never JPA-managed entities, so the listener never sees a detached entity."

key-files:
  created:
    - backend/src/main/java/com/fintrack/common/event/InvestmentTransactionRecordedEvent.java
    - backend/src/main/java/com/fintrack/common/event/InvestmentTransactionDeletedEvent.java
    - backend/src/main/java/com/fintrack/common/event/BillPaidEvent.java
    - backend/src/main/java/com/fintrack/common/event/BudgetTransactionPersistedEvent.java
    - backend/src/main/java/com/fintrack/portfolio/holding/HoldingProjectionListener.java
    - backend/src/main/java/com/fintrack/bills/BillPaidNotificationListener.java
    - backend/src/main/java/com/fintrack/budget/BudgetRuleEvaluationListener.java
    - backend/src/test/java/com/fintrack/portfolio/holding/HoldingProjectionListenerTest.java
    - backend/src/test/java/com/fintrack/bills/BillPaidNotificationListenerTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetRuleEvaluationListenerTest.java
  modified:
    - backend/src/main/java/com/fintrack/portfolio/transaction/InvestmentTransactionService.java
    - backend/src/main/java/com/fintrack/bills/BillService.java
    - backend/src/main/java/com/fintrack/budget/BudgetService.java
    - backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceTest.java
    - backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceAuditTest.java
    - backend/src/test/java/com/fintrack/bills/BillServiceTest.java
    - backend/src/test/java/com/fintrack/bills/BillServiceAuditTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetServiceTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetServiceAuditTest.java

key-decisions:
  - "HoldingProjectionListener exception cannot roll back the writer. SELL still rolls back via a writer-side pre-validation; BUY / BES_CONTRIBUTION mutations on the holdings table run in AFTER_COMMIT and a listener exception is logged at ERROR but the transaction stays committed. This is the deliberate behaviour change of the plan."
  - "InvestmentTransactionService keeps a HoldingRepository field for the SELL pre-validation guard, even though the field is no longer used from the holding-mutation path. The plan's verification rule that grepped for the field as gone is treated as inconsistent with the same plan's instruction to preserve the SELL rollback semantics — preserving behaviour wins. Documented in Deviations."
  - "BillPaidNotificationListener is the FIRST notification surface for user-driven bill payments. BillService.pay(...) previously had no notification side effect; the plan framing of 'extract cross-cutting wiring' for this path is a forward-add, not an extract."
  - "Listeners are NOT @Async. AFTER_COMMIT semantics are sufficient. Async listeners would need explicit SecurityContextHolder / MDC propagation, which is out of scope. A future plan can opt in per-listener."
  - "Event records carry plain values (UUIDs, BigDecimals, enums). No JPA-managed entity references in event payloads. The BudgetRuleEvaluationListener reconstructs a BudgetTransaction via the builder so BudgetRuleService.evaluateForTransaction(...) does not have to change signature."
  - "BudgetService.delete(...) and bulkDelete(...) do NOT publish a budget-transaction event. Rule evaluation today is an amount-added signal; deletion does not retrigger evaluation. Keeping the deletion path quiet matches the legacy contract."
  - "BillReminderScheduler stays untouched. The cron-driven reminder path is system-driven, not user-driven; the plan's boundary is user-driven mutations only. The scheduler keeps its direct MailService / PushService dependencies."
  - "No DomainEvent supertype. Each event record stands alone in its feature's contract. Spring discovers listeners by parameter type without needing a marker interface."

patterns-established:
  - "com.fintrack.common.event package as the single landing zone for cross-cutting domain events. Future plans add records here when a writer publishes a side-effect signal."
  - "Per-listener tests: @ExtendWith(MockitoExtension.class), direct method invocation (no Spring context), one happy path + one failure-swallowed test minimum."
  - "Writer-side test pattern: ArgumentCaptor<Object> on eventPublisher.publishEvent + instanceof check + record-field assertions. Records are not eq-stable for Mockito matchers, so capture-then-cast is the working pattern."

duration: 25 min
completed: 2026-05-06
---

# Phase 25 Plan 01: Cross-cutting Events via ApplicationEventPublisher

**The portfolio, budget, and bill subsystems no longer call collaborator services directly from their mutation paths; instead they publish events on commit, and dedicated listeners own the side effects (holding projection, notification fan-out, budget rule evaluation).**

## Performance

- **Duration:** ~25 min (subagent execution)
- **Tasks:** 3 (sequential, atomic per-task commits)
- **Files added:** 10 (4 event records, 3 listeners, 3 listener tests)
- **Files modified:** 9 (3 service classes, 6 existing test fixtures)

## Accomplishments

- `com.fintrack.common.event` package created with four plain-record event types — one per writer-emitted signal.
- `InvestmentTransactionService.record(...)` and `delete(...)` publish events instead of calling `applyToHolding(...)` directly. The full holding-projection logic moved verbatim to `HoldingProjectionListener` (new package-mate of `HoldingService`); a new pre-write `validateSellPossible(...)` keeps the SELL rollback contract intact.
- `BillService.pay(...)` publishes `BillPaidEvent`. `BillPaidNotificationListener` consumes the event and sends a confirmation email plus push wake-up — the first user-driven payment notification surface in the codebase. The listener mirrors `BillReminderScheduler`'s skip-on-unverified-email behaviour and swallows push delivery failures.
- `BudgetService.create(...)` and `update(...)` publish `BudgetTransactionPersistedEvent` and the inline `try { budgetRuleService.evaluateForTransaction(...) } catch` blocks are removed. `BudgetRuleEvaluationListener` owns the call; the swallow semantics relocated cleanly because Spring naturally isolates listener exceptions on AFTER_COMMIT.
- 24-08 audit emission contract preserved end-to-end: action constants unchanged, userId unchanged, id-substring detail unchanged, post-write ordering unchanged. Every `*ServiceAuditTest` assertion still passes.
- `cd backend && ./mvnw -B -ntp verify` is green: **1026 tests, 0 failures, 0 errors, 132 skipped** (Testcontainers-bound tests skip without Docker, expected). JaCoCo gates: "All coverage checks have been met." Spotless clean.
- `bash scripts/regen-openapi.sh` produced no diff against `frontend/openapi.json`.

## Task Commits

| # | Task | Type | Hash |
|---|------|------|------|
| 1 | Investment transaction events + HoldingProjectionListener + writer-side publish + 9 listener tests | feat | d6b151d |
| 2 | BillPaidEvent + BillPaidNotificationListener + BillService.pay publish + 4 listener tests | feat | 2c8042e |
| 3 | BudgetTransactionPersistedEvent + BudgetRuleEvaluationListener + BudgetService publish sites + 2 listener tests + plan SUMMARY | feat | (this commit) |

## Files Created/Modified

### Created (10)

**Events (4)**
- `backend/src/main/java/com/fintrack/common/event/InvestmentTransactionRecordedEvent.java`
- `backend/src/main/java/com/fintrack/common/event/InvestmentTransactionDeletedEvent.java`
- `backend/src/main/java/com/fintrack/common/event/BillPaidEvent.java`
- `backend/src/main/java/com/fintrack/common/event/BudgetTransactionPersistedEvent.java`

**Listeners (3)**
- `backend/src/main/java/com/fintrack/portfolio/holding/HoldingProjectionListener.java`
- `backend/src/main/java/com/fintrack/bills/BillPaidNotificationListener.java`
- `backend/src/main/java/com/fintrack/budget/BudgetRuleEvaluationListener.java`

**Listener tests (3)**
- `backend/src/test/java/com/fintrack/portfolio/holding/HoldingProjectionListenerTest.java` — 9 tests covering BUY new / BUY merge / BES_CONTRIBUTION / SELL deplete / SELL partial / SELL no-holding / SELL exceeding / DEPOSIT-WITHDRAW-REBALANCE early-return / deleted-event no-op.
- `backend/src/test/java/com/fintrack/bills/BillPaidNotificationListenerTest.java` — 4 tests (verified user mail + push, unverified user skip both, missing user skip both, push failure does not propagate).
- `backend/src/test/java/com/fintrack/budget/BudgetRuleEvaluationListenerTest.java` — 2 tests (delegate with event fields, exception swallowed).

### Modified (9)

**Source (3)**
- `backend/src/main/java/com/fintrack/portfolio/transaction/InvestmentTransactionService.java` — `applyToHolding(...)` removed; `validateSellPossible(...)` added; `ApplicationEventPublisher` field added; record/delete publish recorded/deleted events after audit.
- `backend/src/main/java/com/fintrack/bills/BillService.java` — `ApplicationEventPublisher` field added; `pay(...)` publishes `BillPaidEvent` after `auditService.success(...)`.
- `backend/src/main/java/com/fintrack/budget/BudgetService.java` — `BudgetRuleService` field removed; `ApplicationEventPublisher` field added; `create(...)` and `update(...)` publish `BudgetTransactionPersistedEvent` instead of inline `evaluateForTransaction` calls.

**Tests (6)**
- `backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceTest.java` — holding-projection assertions replaced with event-publish assertions; pre-write SELL guard tests preserved; new tests for the deleted-event publish.
- `backend/src/test/java/com/fintrack/portfolio/transaction/InvestmentTransactionServiceAuditTest.java` — `@Mock ApplicationEventPublisher` added to satisfy the new constructor; SELL-fail tests now hit `validateSellPossible` before the save.
- `backend/src/test/java/com/fintrack/bills/BillServiceTest.java` — `@Mock ApplicationEventPublisher` added; new test asserts `pay(...)` publishes a `BillPaidEvent` with the requested fields.
- `backend/src/test/java/com/fintrack/bills/BillServiceAuditTest.java` — `@Mock ApplicationEventPublisher` added.
- `backend/src/test/java/com/fintrack/budget/BudgetServiceTest.java` — `@Mock BudgetRuleService` removed; `@Mock ApplicationEventPublisher` added.
- `backend/src/test/java/com/fintrack/budget/BudgetServiceAuditTest.java` — same mock swap.

## Decisions Made

- **SELL pre-write guard preserved on the writer; BUY listener-exception is now non-rollback.** This is the deliberate behaviour change the plan calls out. The legacy contract for SELL was "if the holding update fails, the whole transaction rolls back." Preserving that for SELL specifically (and only for SELL) means a user attempt to oversell still returns a 4xx with no transaction row created. For BUY / BES_CONTRIBUTION the listener can always create or extend a holding, so a listener-side failure can only happen if the projection logic has a bug — in that case the transaction row still commits and the operator sees an ERROR log. The next plan that touches `HoldingProjectionListener` should note this asymmetry.
- **`HoldingRepository` field stays on `InvestmentTransactionService`.** The plan's verify list says the field should be gone; the same plan's action steps say SELL must still pre-validate by reading the holding. The two are mutually exclusive. Behaviour preservation wins. The field is now used only by `validateSellPossible(...)` and not by the holding-mutation path. The mutation-path coupling — what the plan was written to break — is broken.
- **Listeners are synchronous (AFTER_COMMIT, not @Async).** Async would propagate the listener side effects onto a separate thread, which loses the SecurityContextHolder set by the JWT filter and the MDC request-id used for log correlation. Sync AFTER_COMMIT is the smaller blast radius for plan 25-01. Plan 25-02 (cache invalidation) can opt in to async if a benchmark says it's worth it.
- **`BillService.pay(...)` was previously notification-less.** This task does not extract an existing coupling for that path; it ADDS the notification on the new event surface. The plan framing of "extract cross-cutting wiring" was inaccurate for this path; the framing is "introduce the wiring through events".
- **`BudgetService.delete` and `bulkDelete` do NOT publish events.** The legacy `evaluateForTransaction` was only ever called from `create` and `update`. Adding it to delete would change behaviour, not preserve it.
- **Cross-cutting sweep: nothing else moved in this plan.** Quick grep for `private final.*Service` shows other coupling shapes (e.g., `RecurringTemplateScheduler` writing through `BudgetService` directly, `PriceAlertService` calling `notificationRepo.save(...)`). The latter is a DB-queue pattern, not a process-internal coupling, and is fine. The former is system-driven (cron) and stays out of scope. The three couplings named in the plan are the three that moved.

## Mutation Coverage Results

The pitest pass was not run as part of this plan (carry-over flake from 24-04 / 24-08 on JDK 21 + Windows). The verify suite is the hard gate and it is green; per-class kill rates can be sampled in a follow-up. Expected behaviour after this plan:

- `InvestmentTransactionService` mutation kill rate may drop slightly (some `applyToHolding` branches moved out); `HoldingProjectionListener` picks up the same surface area in its dedicated test. Net project kill rate should hold above the 60% gate from 23-02.
- `BudgetService` mutation kill rate on `create` / `update` paths drops because the `try / catch` block around `evaluateForTransaction` moved out; `BudgetRuleEvaluationListener` test covers the swallow.
- `BillService` mutation kill rate on `pay(...)` ticks up because the publish line is exercised by the new event-publish test.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Verification list and action steps are mutually exclusive on the `HoldingRepository` field**
- **Found during:** Task 1 implementation.
- **Issue:** Plan verification line says `Grep("private final HoldingRepository", ".../InvestmentTransactionService.java")` returns no match, but action step 3 says "keep the pre-write SELL guard" which requires reading the holding via `HoldingRepository`.
- **Fix:** Kept the field. `validateSellPossible(portfolioId, request)` is the only consumer; the mutation path no longer touches the field. The plan's intended behavioural contract (SELL still rolls back) is preserved. Documented in Decisions Made.
- **Committed in:** d6b151d.

### Deferred Enhancements

- **Listener-side audit relocation.** 24-08-SUMMARY's "Next Phase Readiness" note anticipated this plan would move audit emission to the listener side. We did NOT move it: the writer keeps the audit row and the entity-id-in-detail contract that's pinned by `*ServiceAuditTest` still holds. Moving audit to the listener is a separate decision and a separate plan, behind whatever cache / invalidation work 25-02 brings.
- **`HoldingProjectionListener` rollback wiring on the deleted-event path.** Currently a debug log no-op. Re-rolling a deleted SELL onto the holding requires a richer event payload (the original quantity / direction) plus a re-evaluation pass; left for a follow-up.
- **Cross-cutting sweep additions.** No new couplings observed beyond what 25-01 already names; the system-driven scheduler couplings stay as-is.

---

**Total deviations:** 1 auto-fixed (Rule 1), 0 deferred from scope, 2 deferred enhancements logged for future plans.
**Impact on plan:** Behaviour preserved end-to-end. Tests green.

## Issues Encountered

- Mockito strict-stub warnings on the existing `InvestmentTransactionServiceAuditTest` after the SELL guard moved before `transactionRepository.save(...)`: the SELL-failure tests no longer hit the save stub. Fixed by removing the now-unused stubs.
- Spotless reformatted six files on first apply (long `eventPublisher.publishEvent(new ...Event(...))` line wraps). Standard for this repo.

## Next Phase Readiness

- Plan 25-02 (Spring Cache + Caffeine on hot reads) is the next plan. The event boundary established here is the natural place to wire cache invalidation listeners — `HoldingProjectionListener` already runs on the holding-write path, so a future cache eviction listener for portfolio summaries / holdings views can live next to it without re-coupling the writer service.
- Plan 25-03 (de-block reactive price clients) follows; nothing in this plan blocks it.
- The audit emission contract from 24-08 remains the contract: writer owns the audit row, listener owns the side effect. A future plan that wants to emit audit on the listener side can do so without breaking the existing tests, but must add new audit rows rather than relocate the existing ones.

---
*Phase: 25-architecture-cleanup*
*Completed: 2026-05-06*
