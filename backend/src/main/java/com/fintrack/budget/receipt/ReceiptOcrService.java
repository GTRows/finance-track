package com.fintrack.budget.receipt;

import com.fintrack.common.entity.BudgetTransaction.OcrStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

/**
 * Runs Tesseract over a single receipt image and reports the extracted text plus a terminal status
 * for the worker to persist. The class is deliberately stateless: each invocation creates its own
 * {@link Tesseract} instance because the upstream type is not thread-safe and the worker fans rows
 * out across virtual threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptOcrService {

    private final OcrProperties props;

    /** Result of an OCR attempt. {@code text} is empty when {@code status} is non-success. */
    public record OcrOutcome(OcrStatus status, String text, String error) {}

    public OcrOutcome run(Path imageFile) {
        if (!Files.exists(imageFile)) {
            return new OcrOutcome(OcrStatus.FAILED, "", "Image file missing: " + imageFile);
        }
        try {
            ITesseract engine = newEngine();
            String text = engine.doOCR(imageFile.toFile());
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                return new OcrOutcome(OcrStatus.FAILED, "", "Empty OCR output");
            }
            return new OcrOutcome(OcrStatus.SUCCESS, trimmed, null);
        } catch (TesseractException e) {
            log.warn("Tesseract failed on {}: {}", imageFile, e.getMessage());
            return new OcrOutcome(OcrStatus.FAILED, "", e.getMessage());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            // Transient native error: retry next sweep rather than giving up.
            log.warn("Transient OCR error on {}: {}", imageFile, e.getMessage());
            return new OcrOutcome(OcrStatus.RETRY, "", e.getMessage());
        }
    }

    /** Visible for testing — overridden in tests to inject a stub engine. */
    ITesseract newEngine() {
        Tesseract engine = new Tesseract();
        engine.setDatapath(props.tessdataPath());
        engine.setLanguage(props.languages());
        return engine;
    }

    /** Validates that tessdata files exist on disk. Exposed for the worker startup probe. */
    public boolean tessdataAvailable() {
        File dir = new File(props.tessdataPath());
        if (!dir.isDirectory()) return false;
        for (String lang : props.languages().split("\\+")) {
            if (!new File(dir, lang.trim() + ".traineddata").isFile()) return false;
        }
        return true;
    }
}
