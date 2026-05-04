---
phase: 23-coverage-completion
plan: 04
subsystem: budget
tags: [ocr, tess4j, scheduler, virtual-threads, async, flyway, receipts]

requires:
  - phase: 23-03-coverage-completion
    provides: OpenAPI contract gate and openapi-typescript pipeline; Plan 04 forces a regen of `frontend/openapi.json` and `frontend/src/api/openapi.types.ts` because `TransactionResponse` gains `ocrStatus` and `ocrText` fields.
provides:
  - Flyway V36 adding `ocr_status`, `ocr_text`, `ocr_completed_at` to `transactions`
  - tess4j 5.13.0 wired in `backend/pom.xml`
  - Dockerfile bundles `tessdata_fast` for `tur` + `eng` into the runtime image (~30 MB)
  - `ReceiptOcrService` (stateless, per-call Tesseract instance) and `ReceiptOcrWorker` (`@Scheduled`, virtual-thread fan-out, short per-row transactions, bounded retry)
  - `OcrProperties` configuration record (`fintrack.ocr.*`)
  - `OcrStatus` enum on `BudgetTransaction`
  - `TransactionResponse` exposes `ocrStatus` + `ocrText`
  - Frontend `OcrIndicator` UI in `ReceiptAction` with per-status badges and a click-to-expand text panel
affects:
  - phase: 24-security-hardening
    note: Signed-URL scheme for receipts (D8) will need to keep `ocrText` accessible to the same authenticated owner; the OCR text is a more sensitive surface than the binary file.
  - phase: 27-tax-and-accounts
    note: Capital-gains and bank-import features can mine `ocrText` for merchant/date hints if the OCR pipeline is extended with a parser layer.
  - phase: 30-performance-and-polish
    note: Worker batches at 25 rows on a 60 s poll. If transaction volume grows past ~25/min, increase batch size or poll cadence; the partial index `idx_transactions_ocr_pending` keeps the scan cheap regardless of total row count.

tech-stack:
  added:
    - net.sourceforge.tess4j:tess4j:5.13.0 (Tesseract bindings via JNA)
    - tessdata_fast (tur + eng) bundled at Docker build time
  patterns:
    - Per-row short `@Transactional` blocks (claim/commit) instead of one batch transaction (`.planning/codebase/CONCERNS.md` flagged long transactions as a smell)
    - Virtual-thread executor for fan-out (`Executors.newVirtualThreadPerTaskExecutor()`) so blocking native OCR calls do not pin platform threads
    - Status enum gated on a non-null receipt path; PDFs are stored without OCR (raster-only)

key-files:
  created:
    - backend/src/main/resources/db/migration/V36__add_receipt_ocr.sql
    - backend/src/main/java/com/fintrack/budget/receipt/OcrProperties.java
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptOcrService.java
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptOcrWorker.java
    - backend/src/test/java/com/fintrack/budget/receipt/ReceiptOcrServiceTest.java
    - backend/src/main/resources/tessdata/.gitkeep
    - .planning/phases/23-coverage-completion/23-04-SUMMARY.md
  modified:
    - backend/pom.xml (tess4j dep)
    - backend/Dockerfile (tessdata_fast download stage + copy to runtime)
    - backend/src/main/resources/application.yml (`fintrack.ocr.*`)
    - backend/src/main/java/com/fintrack/FinTrackApplication.java (`@EnableConfigurationProperties(OcrProperties.class)`)
    - backend/src/main/java/com/fintrack/common/entity/BudgetTransaction.java (`OcrStatus` enum + 3 columns)
    - backend/src/main/java/com/fintrack/budget/receipt/ReceiptStorageService.java (queue PENDING on image upload; clear on delete)
    - backend/src/main/java/com/fintrack/budget/TransactionRepository.java (`findReceiptsForOcr` finder)
    - backend/src/main/java/com/fintrack/budget/dto/TransactionResponse.java (expose `ocrStatus` + `ocrText`)
    - backend/src/test/java/com/fintrack/budget/BudgetControllerWebMvcTest.java (constructor arity bumped for the two new record fields)
    - frontend/openapi.json + frontend/src/api/openapi.types.ts (regenerated)
    - frontend/src/types/budget.types.ts (`OcrStatus` type + two fields on `BudgetTransaction`)
    - frontend/src/components/budget/ReceiptAction.tsx (`OcrIndicator` for status + click-to-expand text)
    - frontend/src/pages/BudgetPage.tsx (passes `ocrStatus` + `ocrText`)
    - frontend/src/i18n/locales/en.json + tr.json (5 new receipt OCR keys per locale)
    - tasks/ROADMAP.md (A8, A9, B9 closeout + plans 23-02..04 progress log)
    - .planning/ROADMAP.md (Phase 23 marked complete; 23-03 + 23-04 plans flipped done)
    - .planning/STATE.md (Phase 23 closed; pointer to Phase 24)

key-decisions:
  - **tessdata_fast over tessdata_best.** The `_best` corpus is ~10x larger for marginal gain on receipt-grade images. Keeps the runtime image lean.
  - **Per-row short transactions over one batch transaction.** `.planning/codebase/CONCERNS.md` flags long transactions; processing each row in its own `claim` and `commit` transaction caps lock duration to a single row even if 25 rows fan out concurrently.
  - **Virtual-thread fan-out instead of `@Async` on a fixed pool.** OCR is a blocking native call; virtual threads cost ~zero per blocked thread and the bounded `awaitTermination` keeps a wedged engine from backing up the queue.
  - **No manual retry button this iteration.** RETRY is automatic; failed rows surface a muted alert badge but do not expose a "try again" UI yet (would require a dedicated endpoint that is out of scope for the closeout plan).
  - **PDFs are stored without OCR.** tess4j only handles raster images here; PDFs land with `ocr_status = NULL` so the worker skips them. A future plan can plug in a PDF→image rasterizer if needed.
  - **OCR worker auto-disables when tessdata is missing.** The worker logs a warning at startup and short-circuits each sweep so absent tessdata never crashes the app or thrashes the DB.

patterns-established:
  - Background-enrichment workers in this codebase should: (a) gate on a startup probe that flips a boolean so missing dependencies turn the worker into a no-op rather than a crash loop; (b) claim a row, do work outside a transaction, commit results in a second transaction; (c) use a virtual-thread executor with an explicit `awaitTermination` budget tied to the poll interval.

issues-created:
  - none

duration: ~1h 15m
completed: 2026-05-04
---

# Phase 23 Plan 04: Receipt OCR worker

**Receipts gain searchable OCR text via tess4j worker; A9 closed; Phase 23 complete.**

## Performance

- **Duration:** ~1h 15m (schema + dep wiring; service/worker; DTO + UI surface; openapi regeneration; spotless re-flow; closeout edits)
- **Tasks:** 3 of 3
- **Files modified or created:** ~20
- **Commits:** to follow this summary, one per task plus a closeout

## Accomplishments

- **Schema + tessdata packaging.** V36 adds `ocr_status`, `ocr_text`, `ocr_completed_at` plus a partial index on the worker queue. Dockerfile fetches `tessdata_fast` for `tur` + `eng` and copies them into `/app/tessdata`. `application.yml` exposes `fintrack.ocr.*` with sane defaults.
- **Worker with virtual-thread fan-out and bounded retry.** `ReceiptOcrWorker` polls every 60 s, pulls up to 25 PENDING/RETRY rows older than 5 s (so the upload commit is flushed), fans out across `Executors.newVirtualThreadPerTaskExecutor()`, and persists each result in its own short transaction. RETRY caps via a time-since-completed approximation tied to `props.maxRetries()`.
- **Frontend status + text display.** `OcrIndicator` renders a spinner badge during PENDING/IN_PROGRESS, a clickable `ScanText` icon that toggles a popover with the extracted text on SUCCESS, and a muted `AlertCircle` on FAILED/RETRY. Five i18n keys per locale.
- **Phase 23 closeout.** `tasks/ROADMAP.md` strikes A8, A9 (and partially B9) with Shipped notes; `.planning/ROADMAP.md` flips Phase 23 to complete; `.planning/STATE.md` points at Phase 24 (Security Hardening).

## Decisions Made

- `tessdata_fast` over `tessdata_best`: ~10x smaller, marginal gain on receipts.
- Per-row short transactions over one batch transaction (CONCERNS.md flag).
- No manual retry button this iteration (auto-RETRY only).
- PDFs are stored without OCR (raster-only).
- Worker auto-disables when tessdata is missing (startup probe → warning log + no-op sweeps).

## Deviations from Plan

- **No dedicated `Receipt` entity.** The plan referenced "the existing Receipt entity" but Phase 10.4 stored receipts as a `receipt_path` column on `transactions`, not as a dedicated table. The OCR columns therefore land on `transactions` (V36) rather than a `receipts` table. The end result — searchable text per receipt — is identical.
- **No `ReceiptResponse` DTO update.** The receipt upload response is the file metadata (`StoredReceipt`), not a Receipt projection. OCR status surfaces through the existing `TransactionResponse`, which the frontend already consumes; adding a separate response type would have duplicated the surface.
- **OCR fixture image deferred.** A bundled tessdata setup is required to run a real Tesseract pass, which is not available on this Windows host. The unit tests stub `Tesseract` via the protected `newEngine()` factory, exercising the SUCCESS / FAILED-empty / FAILED-exception / RETRY-transient / FAILED-missing-file paths plus the `tessdataAvailable()` probe. Real-image coverage will land naturally in CI where tessdata is bundled.

## Issues Encountered

- `IOException` removed from the catch chain in `ReceiptOcrService.run` once compilation flagged it as unreachable — `Tesseract.doOCR(File)` only throws `TesseractException`.
- `BudgetControllerWebMvcTest.sampleTxn()` constructed `TransactionResponse` directly with the old arity; bumped to include the two new fields when the record signature changed.
- Spotless reformatted the new files on first verify; reapplied via `./mvnw spotless:apply`.

## Next Step

Phase 23 complete. Ready for `/gsd:plan-phase 24` (Security Hardening).

---
*Phase: 23-coverage-completion*
*Completed: 2026-05-04*
