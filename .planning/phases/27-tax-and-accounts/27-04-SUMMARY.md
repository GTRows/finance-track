---
phase: 27-tax-and-accounts
plan: 04
subsystem: imports
tags: [bank-csv, tr, garanti, isbank, akbank, fingerprint, idempotent, multipart, account-linked-transactions]

requires:
  - phase: 27
    plan: 02
    provides: accounts entity (V41) -- the import service ownership-guards the picked account via accountRepository.findByIdAndUserIdAndArchivedFalse and stamps account_id on every inserted row
  - phase: 27
    plan: 03
    provides: V42 transactions.account_id FK + AccountBalanceListener AFTER_COMMIT writer -- imported rows publish BudgetTransactionPersistedEvent, the listener deltas Account.currentBalance once the import transaction commits
  - phase: 8
    provides: BudgetTransaction entity + BudgetService.create -- the import service inserts via the same persistence path, sharing audit emission + event publication
  - phase: 8
    provides: TransactionCategoryRule regex set -- the new BankCsvCategoryMatcher walks the rules in order and stops at the first match; no new rule schema

provides:
  - V44 import_fingerprint VARCHAR(64) on transactions + partial unique index `WHERE account_id IS NOT NULL AND import_fingerprint IS NOT NULL` for per-account idempotent dedupe
  - com.fintrack.imports.bank package: Bank enum (GARANTI / ISBANK / AKBANK), BankCsvParser interface + 3 implementations, BankCsvParserRegistry @Component, BankCsvCategoryMatcher, BankCsvImportService, BankCsvImportController
  - POST /api/v1/imports/bank/preview + POST /api/v1/imports/bank/commit -- multipart endpoints with bank, accountId, file form parts
  - GlobalExceptionHandler 400-mapping for MissingServletRequestParameterException + MissingServletRequestPartException
  - AuditAction.BANK_CSV_PREVIEWED + BANK_CSV_COMMITTED
  - /imports/bank-csv frontend page with bank picker, account picker, multipart upload, preview pane, commit button + bankCsvImport.* i18n namespace in tr.json + en.json

affects: [28-01]

tech-stack:
  added: []
  patterns:
    - "Per-account fingerprint dedupe via partial unique index. The fingerprint is SHA-256 over `accountId|txnDate|amount|description` and is account-scoped not user-scoped, so the same row across two different accounts (e.g. an inter-account transfer the bank exports on both sides) is NOT a duplicate. The partial index `WHERE account_id IS NOT NULL AND import_fingerprint IS NOT NULL` keeps pre-27-04 NULL rows untouched."
    - "Spring `Map<Bank, BankCsvParser>` injection for parser dispatch. Each parser declares `Bank bank()` and Spring auto-discovers them into a map keyed by the enum. Adding a new bank is a single `@Component` class plus an enum entry plus a fixture file -- no factory wiring."
    - "Non-fatal parser warnings. A single unparseable row surfaces as `parserWarnings: [{row: 7, message: \"...\"}]` on the preview/commit DTO without aborting the import. The operator can decide whether to re-export from the bank or accept the partial set."
    - "Categorisation reuses the existing TransactionCategoryRule regex set verbatim -- no new rule schema, no per-bank rule taxonomy. The matcher walks rules in order and stops at the first match; rules already drive in-app create/edit so the import surface stays consistent with manual entry."
    - "Multipart endpoints behind a 400-handler pair (MissingServletRequestParameterException + MissingServletRequestPartException) so a bad form part gets the consistent ErrorResponse envelope instead of bubbling to the generic 500."

key-files:
  created:
    - backend/src/main/resources/db/migration/V44__bank_csv_import_fingerprint.sql
    - backend/src/main/java/com/fintrack/imports/bank/Bank.java
    - backend/src/main/java/com/fintrack/imports/bank/BankCsvParser.java
    - backend/src/main/java/com/fintrack/imports/bank/BankCsvParserRegistry.java
    - backend/src/main/java/com/fintrack/imports/bank/GarantiCsvParser.java
    - backend/src/main/java/com/fintrack/imports/bank/IsbankCsvParser.java
    - backend/src/main/java/com/fintrack/imports/bank/AkbankCsvParser.java
    - backend/src/main/java/com/fintrack/imports/bank/RawBankRow.java
    - backend/src/main/java/com/fintrack/imports/bank/BankCsvCategoryMatcher.java
    - backend/src/main/java/com/fintrack/imports/bank/BankCsvImportService.java
    - backend/src/main/java/com/fintrack/imports/bank/BankCsvImportController.java
    - backend/src/main/java/com/fintrack/imports/bank/dto/BankCsvImportSummary.java
    - backend/src/main/java/com/fintrack/imports/bank/dto/BankCsvPreviewRow.java
    - backend/src/test/java/com/fintrack/imports/bank/GarantiCsvParserTest.java
    - backend/src/test/java/com/fintrack/imports/bank/IsbankCsvParserTest.java
    - backend/src/test/java/com/fintrack/imports/bank/AkbankCsvParserTest.java
    - backend/src/test/java/com/fintrack/imports/bank/BankCsvCategoryMatcherTest.java
    - backend/src/test/java/com/fintrack/imports/bank/BankCsvImportServiceTest.java
    - backend/src/test/java/com/fintrack/imports/bank/BankCsvImportControllerWebMvcTest.java
    - backend/src/test/resources/bank-csv/garanti-2025-01.csv
    - backend/src/test/resources/bank-csv/isbank-international-2025-01.csv
    - backend/src/test/resources/bank-csv/isbank-legacy-2025-01.csv
    - backend/src/test/resources/bank-csv/akbank-2025-01.csv
    - frontend/src/api/bankcsv.api.ts
    - frontend/src/hooks/useBankCsvImport.ts
    - frontend/src/hooks/useBankCsvImport.test.tsx
    - frontend/src/pages/BankCsvImportPage.tsx
    - frontend/src/types/bankCsv.types.ts
  modified:
    - backend/src/main/java/com/fintrack/audit/AuditAction.java
    - backend/src/main/java/com/fintrack/budget/TransactionRepository.java
    - backend/src/main/java/com/fintrack/common/entity/BudgetTransaction.java
    - backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java
    - backend/src/test/java/com/fintrack/budget/TransactionRepositoryDataJpaTest.java
    - backend/src/test/java/com/fintrack/budget/BudgetControllerWebMvcTest.java
    - frontend/src/App.tsx
    - frontend/src/components/layout/AppShell.tsx
    - frontend/src/i18n/locales/tr.json
    - frontend/src/i18n/locales/en.json
    - docs/OPERATIONS.md
    - .planning/STATE.md
  deliberately-untouched:
    - .env.example -- project deny rule Write/Edit(**/.env.*); no new env vars required (parsers + index live entirely inside the JVM + Postgres)
    - docker-compose.yml -- pre_guard_release_files.py PreToolUse hook; this plan introduces zero infra changes
    - CHANGELOG.md -- pre_guard_release_files.py covers it; per the 26-01 / 26-02 / 26-03 / 27-01 / 27-02 / 27-03 precedent, the changelog entry is described in this SUMMARY and applied by the release flow
    - backend/pom.xml -- no new Maven dep (BufferedReader + a hand-rolled CSV split keep the parsers in stdlib)
    - package.json + package-lock.json -- no new npm dep (multipart upload via FormData + the existing axios client)
    - frontend/openapi.json + frontend/src/api/openapi.types.ts -- the regen script (scripts/regen-openapi.sh) fails on the pre-existing 26-01 OpenTelemetry sdk-autoconfigure ComponentLoader NoClassDefFoundError that affects pre-27-01 / 27-02 / 27-03 / 27-04 commits as well (verified at HEAD). The new endpoint surface is exercised end-to-end by BankCsvImportControllerWebMvcTest

key-decisions:
  - "Per-account fingerprint dedupe via partial unique index, NOT a separate import_runs / import_log audit table. The fingerprint is SHA-256(accountId|txnDate|amount|description) and lives on the transactions row directly. The partial unique index `WHERE account_id IS NOT NULL AND import_fingerprint IS NOT NULL` enforces dedupe at the DB level. Pre-27-04 rows have NULL fingerprint and are untouched. An import_log table would add a join + an N+1 cost on every preview without buying anything the index doesn't already give."
  - "Account-scoped fingerprint, not user-scoped. The same row appearing across two different accounts (e.g. an inter-account transfer the bank exports on both sides) is NOT a duplicate. This matches the operator's mental model -- if the bank shipped two rows, the app should keep two rows."
  - "Three parsers ship in v1: GARANTI / ISBANK / AKBANK. Each is a hand-rolled BufferedReader walker that splits on the bank's delimiter and parses the bank's date / decimal locale. ISBANK supports both legacy + international header variants because the bank ships both formats depending on the account type. Other banks (Yapı Kredi, Ziraat, QNB Finansbank, ...) are deferred to follow-up plans -- the parser interface + registry shape is stable, so adding a new bank is a single @Component + enum entry + fixture file."
  - "Spring Map<Bank, BankCsvParser> injection over a hand-rolled factory. Each parser exposes Bank bank() and Spring auto-discovers them into a map keyed by the enum. The registry validates the picked bank in O(1) and throws IllegalArgumentException on unknown bank, which routes through GlobalExceptionHandler.handleIllegalArgument -> 400."
  - "Non-fatal parser warnings. A single unparseable row surfaces as parserWarnings: [{row: 7, message: \"...\"}] on the preview/commit DTO without aborting the import. The operator decides whether to re-export from the bank or accept the partial set. Aborting would surface the wrong UX -- a bank that exports one weird row in 200 should not block the entire month's import."
  - "Categorisation reuses the existing TransactionCategoryRule regex set verbatim. The matcher walks the operator's rules in order and stops at the first match; rows that match nothing land NULL and are visible in /budget with the (uncategorised) filter. No new rule schema, no per-bank rule taxonomy."
  - "Two-step preview + commit pattern. preview(...) parses, matches categories, computes fingerprints, and returns a summary DTO without writing. commit(...) does the same work atomically inside a @Transactional and inserts only the non-duplicate rows. The frontend never auto-commits -- the operator has to click Commit after scanning the preview. This is load-bearing: the parser warnings + duplicate count + matched categories surface in preview so the operator can spot anomalies before any DB write."
  - "GlobalExceptionHandler gains 400 mappings for MissingServletRequestParameterException + MissingServletRequestPartException. The bank-CSV endpoint is multipart and a missing form part would otherwise bubble to the generic 500 handler, hiding the actual cause from the operator. The new mappings emit the consistent ErrorResponse envelope with code MISSING_PARAMETER / MISSING_PART so the frontend can surface a precise message."
  - "BankCsvImportService.commit emits a single auditService.success(BANK_CSV_COMMITTED) entry per import (NOT one per row) with detail string 'imported=N, duplicates=M, warnings=K'. Per-row audit would explode the audit log on a 500-row month. The operator can drill down to per-row creation via the existing budget transaction list."

duration: 95 min
completed: 2026-05-09
---

# Phase 27 Plan 04: TR Bank CSV Statement Importer

**FinTrack ships a TR bank CSV statement importer at `/imports/bank-csv` that lets the operator upload a month's bank export, pick the bank + target account, preview the parsed rows + matched categories + duplicate count, then commit. Inserted rows land as `BudgetTransaction`s with `account_id` stamped, the 27-03 `AccountBalanceListener` recomputes the account's `current_balance` after commit, and re-uploading the same file is a no-op via the V44 `import_fingerprint` partial unique index. Three parsers ship in v1 (GARANTI / ISBANK / AKBANK) keyed by an enum and dispatched via Spring's `Map<Bank, BankCsvParser>` auto-discovery; categorisation reuses the operator's existing `TransactionCategoryRule` regex set, and parser warnings are non-fatal so a single corrupt row does not abort the import.**

> **Operator Action — none required this plan.**
>
> No new env vars, no new docker services, no new Maven or npm deps. The Flyway migration `V44__bank_csv_import_fingerprint.sql` runs on next backend boot. The first-use guide for the new `/imports/bank-csv` page is in `docs/OPERATIONS.md` -> `## Importing TR bank CSV statements`.

## Performance

- Duration: 95 min (across 7 atomic commits per GSD protocol)
- Tasks executed: 7 / 7
- Files created: 30 (V44 migration + 12 backend Java files + 6 backend test files + 4 backend test fixtures + 5 frontend files + 2 SUMMARY/PLAN docs already present)
- Files modified: 12 (4 backend main + 2 backend test + 4 frontend + OPERATIONS.md + STATE.md)
- Files deliberately untouched: 7 (`.env.example`, `docker-compose.yml`, `CHANGELOG.md`, `backend/pom.xml`, `package.json`, `package-lock.json`, `frontend/openapi.json` + `frontend/src/api/openapi.types.ts`)
- Test count delta backend: +68 (1207 -> 1275). Parser suites + service test + WebMvc + repository round-trip cases. Exceeds the +35 plan target.
- Test count delta frontend: +3 (239 -> 242). useBankCsvImport hook test cases. Meets the plan target.
- Verify status: `./mvnw -B -ntp clean verify` green; JaCoCo 60% / 45% met (`All coverage checks have been met.`); Spotless clean (`558 files clean`). Frontend `npm run lint -- --max-warnings 0`, `npm run typecheck`, `npm run test -- --run`, `npm run build` all green.

## Accomplishments

1. **V44 fingerprint column + partial unique index.** `backend/src/main/resources/db/migration/V44__bank_csv_import_fingerprint.sql` adds `import_fingerprint VARCHAR(64) NULL` to `transactions` plus a partial unique index `uq_transactions_account_fingerprint ON (account_id, import_fingerprint) WHERE account_id IS NOT NULL AND import_fingerprint IS NOT NULL`. Pre-27-04 rows stay at NULL and are not affected by the dedupe constraint. `BudgetTransaction` JPA entity gains the `importFingerprint` field with `@Column(name = "import_fingerprint", length = 64)`; `ddl-auto=validate` is clean.

2. **Three parsers + registry + parser tests.** New `com.fintrack.imports.bank` package: `Bank` enum (GARANTI / ISBANK / AKBANK), `BankCsvParser` interface (`Bank bank()`, `ParseResult parse(InputStream input)` returning `(List<RawBankRow>, List<ParserWarning>)`), three concrete parsers (`GarantiCsvParser`: Windows-1254 / `;` / `dd/MM/yyyy` / `1.234,56`; `IsbankCsvParser`: UTF-8 / `,` / `dd.MM.yyyy` / `1.234,56`; supports both legacy + international header variants; `AkbankCsvParser`: UTF-8 / `;` / `dd.MM.yyyy` / `1234,56`), `BankCsvParserRegistry` `@Component` injecting via Spring's `Map<Bank, BankCsvParser>` auto-discovery (each parser exposes `Bank bank()` for the map key). Four test fixtures live at `backend/src/test/resources/bank-csv/`. Three parser test classes pin the happy path + warning cases per bank.

3. **BankCsvCategoryMatcher + tests.** New `BankCsvCategoryMatcher` consumes the operator's existing `TransactionCategoryRule` set (already wired by 8-04) and walks them in order with `Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE)` against `RawBankRow.description()`, returning `Optional<UUID>` (the matched category id) or `Optional.empty()` for no-match. Test class covers the no-rules-defined case, single-match, multi-rule precedence (first match wins), case-insensitive match, and explicit-no-match.

4. **BankCsvImportService + DTOs + repository fingerprint queries + service tests.** `BankCsvImportService` exposes `preview(userId, bank, accountId, file)` and `commit(userId, bank, accountId, file)`. Both fan out parser → category match → fingerprint compute. `preview` returns a `BankCsvImportSummary(imported=0, duplicates=N, warnings=[...], rows=[...])` without writing; `commit` is `@Transactional`, inserts the non-duplicate rows via `BudgetTransaction` constructor with `accountId` stamped, audits via `auditService.success(BANK_CSV_COMMITTED, "imported=N, duplicates=M, warnings=K")`, and surfaces `BANK_CSV_INVALID` (parse failure / empty file) and `ACCOUNT_NOT_OWNED` (archived or wrong-user account) via `BusinessRuleException`. Two new fingerprint queries land on `TransactionRepository`: `findFingerprintsByAccountAndFingerprintIn(accountId, fingerprints)` and `existsByAccountIdAndImportFingerprint(accountId, fingerprint)` for the round-trip dedupe check.

5. **BankCsvImportController + WebMvc tests + repository round-trip cases.** `BankCsvImportController` mounts two endpoints under `/api/v1/imports/bank/`: `POST /preview` (multipart `bank` + `accountId` + `file`), `POST /commit` (same shape). `GlobalExceptionHandler` gains `@ExceptionHandler(MissingServletRequestParameterException.class)` and `@ExceptionHandler(MissingServletRequestPartException.class)` mapping both to 400 with `ErrorResponse(code=MISSING_PARAMETER|MISSING_PART)`. `BankCsvImportControllerWebMvcTest` pins the multipart contract: 200 happy path with summary JSON, 400 on missing bank, 400 on missing accountId, 400 on missing file, 400 on `BANK_CSV_INVALID`, 400 on `ACCOUNT_NOT_OWNED`. `TransactionRepositoryDataJpaTest` gains 5 additive Docker-gated cases for the fingerprint round-trip, the partial-unique-index dedupe, and the null-fingerprint passthrough.

6. **BankCsvImportPage + hook + API + i18n + sidebar link.** Frontend ships `/imports/bank-csv` lazy-loaded from `App.tsx`, sidebar link in `AppShell.tsx`, `bankcsv.api.ts` axios module with `FormData` builder for the multipart upload, `bankCsv.types.ts` types module, two React Query hooks in `useBankCsvImport.ts` (`useBankCsvPreview` mutation + `useBankCsvCommit` mutation; commit invalidates `['accounts']`, `['accounts', 'totals']`, and `['budget']` keys so the dashboard reflects the import without manual refetch), `BankCsvImportPage.tsx` page with bank dropdown + account picker + file upload + preview pane (row count + first-N rows + matched categories + duplicate count + parser warnings) + commit button + i18n-driven error surfacing. `useBankCsvImport.test.tsx` covers preview happy path, commit invalidates the dashboard query keys, and parser-warning surface.

7. **OPERATIONS.md runbook + STATE.md close + 27-04-SUMMARY.md.** `docs/OPERATIONS.md` gains a new `## Importing TR bank CSV statements` H2 with the monthly workflow (5 steps), per-bank export options for the three shipped parsers, idempotency note, categorisation note, troubleshooting (`BANK_CSV_INVALID` / `ACCOUNT_NOT_OWNED`), and the v1 scope note (current accounts only; credit cards / FX sub-accounts / brokerage cash deferred). `.planning/STATE.md` reflects Phase 27 complete (4/4 plans), the 27-04 decision row, the new resume pointer to Phase 28. `BudgetControllerWebMvcTest.listTransactionsRejectsMissingMonth` swapped from `is5xxServerError()` to `isBadRequest()` to match the now-correct API behaviour after the missing-param handler addition (see Deviations).

## Files Created/Modified

**Created (backend):**
- `backend/src/main/resources/db/migration/V44__bank_csv_import_fingerprint.sql` — fingerprint column + partial unique index.
- `backend/src/main/java/com/fintrack/imports/bank/Bank.java` — enum (GARANTI / ISBANK / AKBANK).
- `backend/src/main/java/com/fintrack/imports/bank/BankCsvParser.java` — parser interface.
- `backend/src/main/java/com/fintrack/imports/bank/BankCsvParserRegistry.java` — Spring `Map<Bank, BankCsvParser>` registry.
- `backend/src/main/java/com/fintrack/imports/bank/GarantiCsvParser.java` — Windows-1254 / `;` / `dd/MM/yyyy` / `1.234,56`.
- `backend/src/main/java/com/fintrack/imports/bank/IsbankCsvParser.java` — UTF-8 / `,` / `dd.MM.yyyy` / `1.234,56`; supports legacy + international header variants.
- `backend/src/main/java/com/fintrack/imports/bank/AkbankCsvParser.java` — UTF-8 / `;` / `dd.MM.yyyy` / `1234,56`.
- `backend/src/main/java/com/fintrack/imports/bank/RawBankRow.java` — record `(LocalDate txnDate, BigDecimal amount, String description)`.
- `backend/src/main/java/com/fintrack/imports/bank/BankCsvCategoryMatcher.java` — regex resolver against `TransactionCategoryRule`.
- `backend/src/main/java/com/fintrack/imports/bank/BankCsvImportService.java` — `preview` + `commit` + audit emission + `BANK_CSV_INVALID` / `ACCOUNT_NOT_OWNED` mapping.
- `backend/src/main/java/com/fintrack/imports/bank/BankCsvImportController.java` — multipart `POST /preview` + `POST /commit`.
- `backend/src/main/java/com/fintrack/imports/bank/dto/BankCsvImportSummary.java` — DTO `(imported, duplicates, warnings, rows)`.
- `backend/src/main/java/com/fintrack/imports/bank/dto/BankCsvPreviewRow.java` — DTO `(txnDate, amount, description, categoryId, categoryName, fingerprint, duplicate)`.
- 6 backend test classes covering parsers / matcher / service / controller.
- 4 backend test fixtures under `backend/src/test/resources/bank-csv/`.

**Created (frontend):**
- `frontend/src/api/bankcsv.api.ts` — axios module with `FormData` builder.
- `frontend/src/hooks/useBankCsvImport.ts` — `useBankCsvPreview` + `useBankCsvCommit` React Query mutations.
- `frontend/src/hooks/useBankCsvImport.test.tsx` — 3 cases pinning preview / commit / invalidation surface.
- `frontend/src/pages/BankCsvImportPage.tsx` — page with bank picker + account picker + upload + preview pane + commit button.
- `frontend/src/types/bankCsv.types.ts` — typed contract module.

**Modified:**
- `backend/src/main/java/com/fintrack/audit/AuditAction.java` — `BANK_CSV_PREVIEWED` + `BANK_CSV_COMMITTED` constants.
- `backend/src/main/java/com/fintrack/budget/TransactionRepository.java` — fingerprint round-trip queries.
- `backend/src/main/java/com/fintrack/common/entity/BudgetTransaction.java` — `importFingerprint` column field.
- `backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java` — 400 mapping for `MissingServletRequestParameterException` + `MissingServletRequestPartException`.
- `backend/src/test/java/com/fintrack/budget/TransactionRepositoryDataJpaTest.java` — 5 additive Docker-gated cases.
- `backend/src/test/java/com/fintrack/budget/BudgetControllerWebMvcTest.java` — `listTransactionsRejectsMissingMonth` switched from `is5xxServerError()` to `isBadRequest()` to match the now-correct API behaviour after the missing-param handler addition.
- `frontend/src/App.tsx` — lazy import + `/imports/bank-csv` route inside `<ProtectedRoute>`.
- `frontend/src/components/layout/AppShell.tsx` — sidebar link.
- `frontend/src/i18n/locales/tr.json` — `bankCsvImport.*` namespace.
- `frontend/src/i18n/locales/en.json` — same key set in English.
- `docs/OPERATIONS.md` — new `## Importing TR bank CSV statements` H2.
- `.planning/STATE.md` — Phase 27 complete (4/4 plans), 27-04 decision row, resume pointer.

**Deliberately untouched:**
- `.env.example` — project deny rule `Write/Edit(**/.env.*)`. No new env vars; parsers + index live entirely inside the JVM + Postgres.
- `docker-compose.yml` — `pre_guard_release_files.py` PreToolUse hook. Plan introduces zero infra changes.
- `CHANGELOG.md` — also covered by the release-files guard. Per the 26-01 / 26-02 / 26-03 / 27-01 / 27-02 / 27-03 precedent, the changelog entry is described in this SUMMARY and applied by the release flow.
- `backend/pom.xml` — no new Maven dep.
- `package.json` + `package-lock.json` — no new npm dep (multipart upload via the platform `FormData` + the existing axios client).
- `frontend/openapi.json` + `frontend/src/api/openapi.types.ts` — see Deviations.

## Decisions Made

1. **Per-account fingerprint dedupe via partial unique index, NOT a separate `import_runs` audit table.** The fingerprint is `SHA-256(accountId|txnDate|amount|description)` and lives on the `transactions` row directly. Pre-27-04 rows have NULL fingerprint and are untouched.
2. **Account-scoped, not user-scoped, fingerprint** — the same row appearing across two different accounts (e.g. an inter-account transfer) is NOT a duplicate.
3. **Three parsers ship in v1: GARANTI / ISBANK / AKBANK.** Other banks (Yapı Kredi, Ziraat, QNB Finansbank, ...) are deferred to follow-up plans.
4. **Spring `Map<Bank, BankCsvParser>` injection over a hand-rolled factory.** Adding a new bank is a single `@Component` + enum entry + fixture file.
5. **Non-fatal parser warnings.** A single corrupt row surfaces in `parserWarnings` without aborting the import.
6. **Categorisation reuses the existing `TransactionCategoryRule` regex set verbatim** — no new rule schema.
7. **Two-step preview + commit pattern.** The frontend never auto-commits; the operator scans the preview and clicks Commit.
8. **`GlobalExceptionHandler` gains 400 mappings for `MissingServletRequestParameterException` + `MissingServletRequestPartException`** so a bad multipart part gets the consistent `ErrorResponse` envelope instead of the generic 500.
9. **Single audit entry per import (not per row)** — `BANK_CSV_COMMITTED` carries `imported=N, duplicates=M, warnings=K` detail; per-row audit would explode the audit log on a 500-row month.
10. **No `@Observed` annotations** — the 26-01 servlet observation handler auto-instruments every `@RestController` method; the parser layer does sub-millisecond work per row.

## Mutation Coverage Results

`pitest` is opt-in via the `mutation` Maven profile and is NOT part of this plan's verification. The project-level 60% / 45% JaCoCo gate runs on every `verify` and is green after this plan (`All coverage checks have been met.`).

## Deviations from Plan

- **`BudgetControllerWebMvcTest.listTransactionsRejectsMissingMonth` assertion drift fixed.** The test was asserting `is5xxServerError()` on a missing `?month` param. The new `MissingServletRequestParameterException` handler (added in Task 5 to give the bank-CSV multipart endpoint a clean 400) now correctly maps the missing-param case to 400, so the assertion was updated to `isBadRequest()`. The test name `Rejects` was always the load-bearing intent; the prior 5xx was a side-effect of no handler being registered. This is a side-effect of Task 5's handler addition rolled into Task 7's commit.
- **OpenAPI spec regen still defers** per the pre-existing 26-01 OpenTelemetry sdk-autoconfigure `ComponentLoader` `NoClassDefFoundError` (verified at HEAD). The new `/imports/bank/*` endpoint surface is exercised end-to-end by `BankCsvImportControllerWebMvcTest`. The 23-03 contract gate will catch drift the moment the regen script is fixed; that fix should be its own follow-up plan.
- **Credit-card statements / FX sub-accounts / brokerage cash statements out of scope.** The v1 parsers cover only TR current accounts because the FX-rate / per-row fee splits in those formats are not modelled by `RawBankRow`. Deferred to follow-up plans.

## Issues Encountered

- **The first incremental verify run fooled the Maven test gate** because the `tail -100` in the bash invocation masked the test failure inside the gate's output, while the `BUILD FAILURE` line lived above the tail window. Caught and resolved by `mvnw clean verify` after fixing the `BudgetControllerWebMvcTest` assertion.
- **Maven incremental compile cached a stale dependency state** between two consecutive `verify` runs, surfacing a spurious `class file for io.sentry.SentryOptions.BeforeSendCallback not found` error on the second run. `mvnw clean verify` resolved it; the underlying classpath is intact.

## Next Phase Readiness

- **Phase 27 complete (4 / 4 plans).** Tax helper (27-01), accounts entity (27-02), transactions linked to accounts + emergency-fund tile (27-03), and bank CSV importer (27-04) all shipped.
- **Phase 28 candidates** seeded by this plan: cross-currency rollup with FX-rate snapshots for the emergency-fund tile (deferred from 27-03), credit-card statement parser (deferred from 27-04), brokerage cash statement parser (deferred from 27-04), additional bank parsers (Yapı Kredi / Ziraat / QNB / ...), per-month rolling realized-gain Recharts line on the TR tax page (deferred from 27-01), CSV/PDF export of the tax report.

## Next Step

Phase 27 complete (4 / 4 plans). Next: Phase 28 (Rebalance & Emergency Fund). Run `/gsd:plan-phase 28 01`.
