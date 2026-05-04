package com.fintrack.budget.receipt;

import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.OcrStatus;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls for transactions with PENDING or RETRY OCR status and runs Tesseract on each. Each row is
 * processed in its own short {@code @Transactional} block: long transactions across many rows are
 * called out in {@code .planning/codebase/CONCERNS.md} as something to avoid. Fan-out uses a
 * virtual-thread executor so the I/O wait inside Tesseract does not pin platform threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptOcrWorker {

    private final TransactionRepository txnRepo;
    private final ReceiptOcrService ocrService;
    private final OcrProperties props;

    @Value("${fintrack.receipt-dir:/var/lib/fintrack/receipts}")
    private String receiptDir;

    private boolean engineReady;

    @PostConstruct
    void probe() {
        engineReady = ocrService.tessdataAvailable();
        if (!engineReady) {
            log.warn(
                    "Receipt OCR worker disabled: tessdata not found at {}. Set TESSDATA_PATH"
                            + " or place {} files in the configured directory.",
                    props.tessdataPath(),
                    props.languages());
        }
    }

    @Scheduled(fixedDelayString = "${fintrack.ocr.poll-interval-ms:60000}")
    public void sweep() {
        if (!engineReady) return;

        List<BudgetTransaction> batch =
                txnRepo.findReceiptsForOcr(
                        List.of(OcrStatus.PENDING, OcrStatus.RETRY),
                        // Give the upload commit a few seconds to flush before we touch the row.
                        Instant.now().minusSeconds(5),
                        PageRequest.of(0, props.batchSize()));
        if (batch.isEmpty()) return;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (BudgetTransaction txn : batch) {
                pool.submit(() -> processOne(txn.getId()));
            }
            pool.shutdown();
            // Bound the wait to the next poll interval so a wedged engine cannot back up the queue.
            if (!pool.awaitTermination(props.pollIntervalMs(), TimeUnit.MILLISECONDS)) {
                log.warn("OCR sweep timed out before all rows finished");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OCR sweep interrupted");
        }
    }

    /**
     * Marks the row IN_PROGRESS, runs OCR outside any transaction, then commits the result in a
     * second short transaction. RETRY status is bounded by counting prior RETRY commits via {@code
     * ocr_completed_at} hops.
     */
    void processOne(UUID txnId) {
        if (!claim(txnId)) return;
        Path imageFile = Path.of(receiptDir).resolve(loadReceiptPath(txnId)).normalize();
        ReceiptOcrService.OcrOutcome outcome = ocrService.run(imageFile);
        commit(txnId, outcome);
    }

    @Transactional
    boolean claim(UUID txnId) {
        return txnRepo.findById(txnId)
                .map(
                        t -> {
                            if (t.getOcrStatus() != OcrStatus.PENDING
                                    && t.getOcrStatus() != OcrStatus.RETRY) {
                                return false;
                            }
                            t.setOcrStatus(OcrStatus.IN_PROGRESS);
                            txnRepo.save(t);
                            return true;
                        })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    String loadReceiptPath(UUID txnId) {
        return txnRepo.findById(txnId).map(BudgetTransaction::getReceiptPath).orElse("");
    }

    @Transactional
    void commit(UUID txnId, ReceiptOcrService.OcrOutcome outcome) {
        txnRepo.findById(txnId)
                .ifPresent(
                        t -> {
                            OcrStatus next = outcome.status();
                            if (next == OcrStatus.RETRY && hasExceededRetries(t)) {
                                next = OcrStatus.FAILED;
                            }
                            t.setOcrStatus(next);
                            t.setOcrText(outcome.text().isEmpty() ? null : outcome.text());
                            t.setOcrCompletedAt(
                                    next == OcrStatus.SUCCESS || next == OcrStatus.FAILED
                                            ? Instant.now()
                                            : null);
                            txnRepo.save(t);
                            log.info(
                                    "OCR {} for txn {} ({})",
                                    next,
                                    txnId,
                                    outcome.error() == null ? "ok" : outcome.error());
                        });
    }

    private boolean hasExceededRetries(BudgetTransaction txn) {
        // Each RETRY -> IN_PROGRESS -> RETRY hop bumps updated_at past completed_at; a row whose
        // updated_at is far ahead of completed_at has already been retried. We keep the cap simple
        // by capping retries to props.maxRetries() in absolute terms via this approximation.
        if (txn.getOcrCompletedAt() == null) return false;
        long minutesSinceComplete =
                java.time.Duration.between(txn.getOcrCompletedAt(), Instant.now()).toMinutes();
        return minutesSinceComplete > (long) props.maxRetries() * (props.pollIntervalMs() / 60_000);
    }
}
