---
phase: 24-security-hardening
plan: 05
subsystem: audit
tags: [audit, retention, redaction, scheduler, gdpr, flyway]

requires:
  - phase: 23-coverage-completion
    provides: AuditLogRepositoryDataJpaTest baseline (slice + Testcontainers harness extended here)
  - phase: 24-security-hardening
    provides: 24-04 audit detail truncation contract (first-8-hex fingerprint logging respected by the redactor)

provides:
  - AuditPiiRedactor strips email / JWT / IPv4 / IPv6 / TOTP recovery code patterns from audit detail before save and log
  - AuditService.record now redacts before truncating to 500 chars
  - AuditRetentionProperties (@ConfigurationProperties prefix=fintrack.audit) bound from env vars
  - AuditLogRepository.deleteOldestBatch (native Postgres DELETE WHERE id IN SELECT LIMIT) and deleteByCreatedAtBefore (derived) and findOldestCreatedAt (JPQL MIN)
  - AuditRetentionWorker @Scheduled daily at 03:30 with chunked deletion, 100-iteration safety cap, AUDIT_RETENTION_PRUNED audit emission
  - GET /api/v1/admin/audit/retention returns RetentionStatusResponse(retentionDays, batchSize, enabled, oldestEntry, totalEntries)
  - Flyway V40 aligns refresh_tokens.fingerprint to VARCHAR(64) (Hibernate strict-validation compliance)

affects: [24-07, 24-08]

tech-stack:
  added: []
  patterns:
    - "AuditService is the single redaction boundary: callers do not redact, the redactor is not exposed beyond the audit package."
    - "Retention worker chunks at the SQL level (DELETE ... WHERE id IN SELECT ... LIMIT) and intentionally avoids @Transactional so each batch commits independently."
    - "@ConfigurationProperties record with compact-constructor defaults (record(int, int, boolean) clamping non-positive values)."

key-files:
  created:
    - backend/src/main/java/com/fintrack/audit/AuditPiiRedactor.java
    - backend/src/main/java/com/fintrack/audit/AuditRetentionProperties.java
    - backend/src/main/java/com/fintrack/audit/AuditRetentionWorker.java
    - backend/src/main/java/com/fintrack/audit/dto/RetentionStatusResponse.java
    - backend/src/main/resources/db/migration/V40__align_refresh_token_fingerprint_type.sql
    - backend/src/test/java/com/fintrack/audit/AuditPiiRedactorTest.java
    - backend/src/test/java/com/fintrack/audit/AuditRetentionWorkerTest.java
    - backend/src/test/java/com/fintrack/audit/AuditRetentionControllerWebMvcTest.java
  modified:
    - backend/src/main/java/com/fintrack/audit/AuditService.java
    - backend/src/main/java/com/fintrack/audit/AuditController.java
    - backend/src/main/java/com/fintrack/audit/AuditLogRepository.java
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/java/com/fintrack/FinTrackApplication.java
    - backend/src/main/resources/application.yml
    - backend/src/test/java/com/fintrack/audit/AuditServiceTest.java
    - backend/src/test/java/com/fintrack/audit/AuditLogRepositoryDataJpaTest.java
    - frontend/openapi.json
    - frontend/src/api/openapi.types.ts

key-decisions:
  - "Redactor lives in com.fintrack.audit, called once at AuditService.record(). Callers never redact; no RedactionPolicy interface."
  - "Recovery-code regex matches the actual TotpRecoveryCodeService format (5+5 alphanumerics from a 30-char alphabet, dash separator) — plan stated 4+4+4 which was wrong."
  - "AuditRetentionWorker placed in com.fintrack.audit (matches feature-package convention used by ReceiptOcrWorker, BillReminderScheduler, etc.) — plan suggested a new com.fintrack.scheduler root package which does not exist in this repo."
  - "Retention sweeper is NOT @Transactional. Each batch commits independently so a slow run never holds a long lock."
  - "Cron is hard-coded `0 30 3 * * *`. The kill switch is the env-bound `enabled` flag; making the cron itself env-bound is overengineering."
  - "No /trigger-prune endpoint. The scheduler is the only trigger; otherwise it is just one more admin write surface to lock down."
  - "Prometheus metrics deferred to Phase 26 Observability — no per-action metric in this plan."
  - "V40 migration converts refresh_tokens.fingerprint from CHAR(64) to VARCHAR(64). CHAR pads with trailing spaces in Postgres which would silently corrupt hex hash comparisons; VARCHAR matches the JPA default and removes the schema-validation drift."

patterns-established:
  - "PII redaction at the storage boundary: AuditService.record runs detail through AuditPiiRedactor before truncating to DETAIL_MAX, so log lines and DB rows share the same scrubbed payload."
  - "@EnableConfigurationProperties on FinTrackApplication for AuditRetentionProperties (existing pattern reused)."
  - "DataJpaTest extension for createdAt-sensitive scenarios: override CreationTimestamp via TestEntityManager.getEntityManager().createNativeQuery to backdate rows since @CreationTimestamp ignores manual setters."

issues-created: []

duration: 18 min
completed: 2026-05-05
---

# Phase 24 Plan 05: Audit Retention + PII Redaction Summary

**Audit details are scrubbed of email/JWT/IP/recovery-code patterns before storage; a daily scheduler prunes rows past the configured retention window.**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-05-04T17:00:00Z (subagent kickoff)
- **Completed:** 2026-05-05T07:15:00Z (after openapi regen + V40 fix in main session)
- **Tasks:** 3 (plus 1 fix commit for V40)
- **Files modified:** 18 (8 new, 10 modified)

## Accomplishments

- `AuditPiiRedactor` redacts five PII pattern families (email, JWT, IPv4, IPv6, TOTP recovery code) with lookaround-anchored regexes; wired into `AuditService.record(...)` before truncation so DB rows and log lines share the same scrubbed payload.
- `AuditRetentionWorker` runs `@Scheduled(cron = "0 30 3 * * *")`, deletes in 1000-row chunks via a native Postgres `DELETE ... WHERE id IN (SELECT id ... ORDER BY id LIMIT :limit)`, caps at 100 iterations, and emits a single `AUDIT_RETENTION_PRUNED` audit success per run with `deleted=N cutoff=...`.
- Configuration is bound from `AUDIT_RETENTION_DAYS`, `AUDIT_RETENTION_BATCH_SIZE`, `AUDIT_RETENTION_ENABLED` (defaults 90 / 1000 / true) via an `AuditRetentionProperties` record; `@PostConstruct init()` logs the resolved values at boot.
- `GET /api/v1/admin/audit/retention` returns the live config plus `oldestEntry` and `totalEntries`. Authorization is the existing `/api/v1/admin/**` ROLE_ADMIN gate at the security config layer; no per-method `@PreAuthorize` was added.
- V40 migration aligns `refresh_tokens.fingerprint` from CHAR(64) to VARCHAR(64) so Hibernate strict schema validation passes (caught while regenerating the OpenAPI spec).
- OpenAPI artefacts regenerated: `frontend/openapi.json` + `frontend/src/api/openapi.types.ts` carry the new `RetentionStatusResponse` schema and the `/retention` operation.

## Task Commits

| # | Task | Type | Hash |
|---|------|------|------|
| 1 | AuditPiiRedactor + AuditService wiring + 15 redactor unit tests + AuditServiceTest extensions | feat | d9de6ea |
| 2 | AuditRetentionProperties, native chunked deleteOldestBatch, AuditRetentionWorker scheduler, AUDIT_RETENTION_PRUNED action, application.yml bindings, repo + worker tests | feat | 9a016ac |
| - | V40 migration: align refresh_tokens.fingerprint to VARCHAR(64) (24-04 schema-validation bug found via OpenApiSpecGeneratorTest) | fix | baf11e0 |
| 3 | Admin retention status endpoint (DTO, repo MIN query, controller method) + WebMvc test + OpenAPI regen | feat | fac6fec |

Plan metadata commit: see `git log` after this SUMMARY.

## Files Created/Modified

- `backend/src/main/java/com/fintrack/audit/AuditPiiRedactor.java` - regex-based scrubber, single static `LinkedHashMap<Pattern,String>` ordering matters.
- `backend/src/main/java/com/fintrack/audit/AuditService.java` - injected redactor; `record(...)` redacts before truncating.
- `backend/src/main/java/com/fintrack/audit/AuditRetentionProperties.java` - `@ConfigurationProperties` record with compact-constructor defaults.
- `backend/src/main/java/com/fintrack/audit/AuditRetentionWorker.java` - `@Scheduled` daily worker, `@PostConstruct init()` log line, 100-iteration safety cap, no `@Transactional`.
- `backend/src/main/java/com/fintrack/audit/AuditController.java` - new `GET /retention` endpoint, takes `AuditRetentionProperties` via constructor.
- `backend/src/main/java/com/fintrack/audit/AuditLogRepository.java` - `deleteByCreatedAtBefore`, `deleteOldestBatch` (native), `findOldestCreatedAt` (JPQL MIN).
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` - `AUDIT_RETENTION_PRUNED`.
- `backend/src/main/java/com/fintrack/audit/dto/RetentionStatusResponse.java` - 5-field record.
- `backend/src/main/java/com/fintrack/FinTrackApplication.java` - `@EnableConfigurationProperties(AuditRetentionProperties.class)` (subagent verified existing scan didn't pick it up).
- `backend/src/main/resources/application.yml` - `fintrack.audit.{retention-days,batch-size,enabled}` bindings.
- `backend/src/main/resources/db/migration/V40__align_refresh_token_fingerprint_type.sql` - one-line ALTER COLUMN.
- `backend/src/test/java/com/fintrack/audit/AuditPiiRedactorTest.java` - 15 cases covering each pattern, idempotence, null/blank, mixed-PII strings.
- `backend/src/test/java/com/fintrack/audit/AuditServiceTest.java` - constructor switch + a new test that proves the redactor runs before truncation.
- `backend/src/test/java/com/fintrack/audit/AuditLogRepositoryDataJpaTest.java` - two new cases for `deleteOldestBatch` (cutoff respect + chunk loop).
- `backend/src/test/java/com/fintrack/audit/AuditRetentionWorkerTest.java` - disabled-config skip, multi-batch loop summed totals, safety cap, exception propagation.
- `backend/src/test/java/com/fintrack/audit/AuditRetentionControllerWebMvcTest.java` - populated 200 + empty-table null-oldest cases (security gate is global so 403 path is covered by the `/api/v1/admin/**` rule, not per-controller).
- `frontend/openapi.json`, `frontend/src/api/openapi.types.ts` - new `/retention` operation + `RetentionStatusResponse` schema (regen via `bash scripts/regen-openapi.sh` followed by `npm run gen:api-types`).

## Decisions Made

- Worker package: `com.fintrack.audit` not `com.fintrack.scheduler` — feature-aligned with `ReceiptOcrWorker`, `BillReminderScheduler`, `MonthlyReportScheduler`, `PriceScheduler`, `SnapshotScheduler`, `RecurringTemplateScheduler`. The plan's claim that `com.fintrack.scheduler` was the established pattern was wrong.
- Recovery-code regex: `(?<![A-Z2-9])[A-Z2-9]{5}-[A-Z2-9]{5}(?![A-Z2-9])` matching the actual `TotpRecoveryCodeService.newCode()` shape (5+5 alphanumerics from a 30-char alphabet). The plan's `4+4+4` example was incorrect; the redactor and the docstring both reference the source service so a future format change at one site triggers the other.
- Retention worker is NOT `@Transactional`. Each `deleteOldestBatch` invocation is committed by Spring's default `@Modifying` transaction; the worker just loops and sums.
- 100-iteration safety cap on the deletion loop. Bounds the worst case (e.g., a sudden 100k-row backlog at batch=1000 still completes in 100 commits, ~minutes).
- No `/trigger-prune` admin endpoint. The scheduler is the only trigger.
- No per-action Prometheus metric. Observability is Phase 26 territory.
- V40 column-type fix preferred over amending V39. Migrations are immutable once committed; the cleanest correction is forward-only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Recovery-code regex format mismatch**
- **Found during:** Task 1 (AuditPiiRedactor specification).
- **Issue:** Plan suggested `^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$` (Crockford-style 4+4+4). The actual `TotpRecoveryCodeService.newCode()` produces 11-char codes formatted `XXXXX-XXXXX` (5 chars + dash + 5 chars) from the alphabet `ABCDEFGHJKMNPQRSTVWXYZ23456789`.
- **Fix:** Used `(?<![A-Z2-9])[A-Z2-9]{5}-[A-Z2-9]{5}(?![A-Z2-9])` and added a class-level Javadoc anchoring the regex to `TotpRecoveryCodeService.newCode()` so a future format change is caught at code review.
- **Files modified:** `backend/src/main/java/com/fintrack/audit/AuditPiiRedactor.java`.
- **Verification:** Two of the 15 redactor unit tests exercise the recovery-code branch (plain + embedded).
- **Committed in:** d9de6ea (Task 1 commit).

**2. [Rule 1 - Bug] Wrong scheduler package in plan**
- **Found during:** Task 2 (worker placement).
- **Issue:** Plan said `com.fintrack.scheduler` was the established convention. That package does not exist; every existing scheduler/worker lives with its feature (e.g., `com.fintrack.bills.BillReminderScheduler`, `com.fintrack.budget.receipt.ReceiptOcrWorker`).
- **Fix:** Placed `AuditRetentionWorker` and `AuditRetentionWorkerTest` in `com.fintrack.audit`.
- **Files modified:** package paths on the new files.
- **Verification:** `mvnw verify` boots the worker bean without wiring tweaks; `@PostConstruct init()` log line confirmed at startup.
- **Committed in:** 9a016ac (Task 2 commit).

**3. [Rule 1 - Bug] V39 schema-validation drift on refresh_tokens.fingerprint**
- **Found during:** Task 3 (`bash scripts/regen-openapi.sh` after the new endpoint was added).
- **Issue:** Plan 24-04's V39 created `fingerprint CHAR(64)`. JPA's default `@Column(length = 64)` maps to VARCHAR. Hibernate strict schema validation (triggered by `OpenApiSpecGeneratorTest`'s full `@SpringBootTest`) failed: "wrong column type encountered in column [fingerprint] in table [refresh_tokens]; found [bpchar (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]". CHAR also pads with trailing spaces in Postgres, which would silently corrupt 64-char hex comparisons.
- **Fix:** Forward-only V40 `ALTER TABLE refresh_tokens ALTER COLUMN fingerprint TYPE VARCHAR(64);`.
- **Files modified:** `backend/src/main/resources/db/migration/V40__align_refresh_token_fingerprint_type.sql`.
- **Verification:** OpenApiSpecGeneratorTest now passes (1/1, 19.47s); openapi.json regenerated; `mvnw verify` remained green.
- **Committed in:** baf11e0 (separate `fix(24-05):` commit before Task 3).

### Deferred Enhancements

None - no items logged to `.planning/ISSUES.md`.

---

**Total deviations:** 3 auto-fixed (3 Rule 1 bugs), 0 deferred.
**Impact on plan:** The recovery-code and scheduler-package corrections kept the implementation aligned with reality; the V40 fix recovered from a latent 24-04 bug. No scope drift.

## Issues Encountered

- Manual smoke test of the 03:30 cron firing was not run (autonomous execution cannot wait until 03:30). The unit + WebMvc tests cover the deletion loop, the disabled-config skip, the 100-iteration cap, and the controller response shape; a real-time cron fire is left to manual ops verification.
- Subagent that ran most of plan 24-05 was interrupted mid-Task-3 by an Anthropic usage-cap reset; main session picked up Task 3's commit + V40 fix + openapi regen + SUMMARY/STATE/ROADMAP.

## Next Phase Readiness

- Audit detail PII is scrubbed at the single AuditService.record boundary; future plans (especially 24-08 AuditService coverage for portfolio/budget/bill mutations) inherit the redaction without extra wiring.
- Retention is bounded by env-driven config; 24-07 prod fail-fast can wrap a check around `AUDIT_RETENTION_ENABLED` if the owner wants a hard guarantee in production.
- Refresh-token fingerprint storage is now schema-stable; a future V41 is not needed.
- No blockers for plan 24-06 (D8 signed URL scheme for receipts).

---
*Phase: 24-security-hardening*
*Completed: 2026-05-05*
