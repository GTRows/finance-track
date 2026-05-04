package com.fintrack.budget.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.BudgetTransaction.OcrStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReceiptOcrServiceTest {

    private final OcrProperties props = new OcrProperties("/tmp/tessdata", "eng", 25, 60_000, 3);

    @Test
    void runReturnsSuccessOnNonEmptyOcrOutput(@TempDir Path tmp) throws Exception {
        Path image = Files.write(tmp.resolve("receipt.png"), new byte[] {1, 2, 3});
        ITesseract stub = mock(ITesseract.class);
        when(stub.doOCR(any(File.class))).thenReturn("  Migros 14 Nisan  \nTotal 100.00 TRY\n");
        ReceiptOcrService service =
                new ReceiptOcrService(props) {
                    @Override
                    ITesseract newEngine() {
                        return stub;
                    }
                };

        ReceiptOcrService.OcrOutcome outcome = service.run(image);

        assertThat(outcome.status()).isEqualTo(OcrStatus.SUCCESS);
        assertThat(outcome.text()).startsWith("Migros 14 Nisan");
        assertThat(outcome.error()).isNull();
    }

    @Test
    void runReturnsFailedWhenOcrOutputIsEmpty(@TempDir Path tmp) throws Exception {
        Path image = Files.write(tmp.resolve("blank.png"), new byte[] {0});
        ITesseract stub = mock(ITesseract.class);
        when(stub.doOCR(any(File.class))).thenReturn("   \n  ");
        ReceiptOcrService service =
                new ReceiptOcrService(props) {
                    @Override
                    ITesseract newEngine() {
                        return stub;
                    }
                };

        ReceiptOcrService.OcrOutcome outcome = service.run(image);

        assertThat(outcome.status()).isEqualTo(OcrStatus.FAILED);
        assertThat(outcome.text()).isEmpty();
        assertThat(outcome.error()).isEqualTo("Empty OCR output");
    }

    @Test
    void runReturnsFailedOnTesseractException(@TempDir Path tmp) throws Exception {
        Path image = Files.write(tmp.resolve("corrupt.png"), new byte[] {0});
        ITesseract stub = mock(ITesseract.class);
        when(stub.doOCR(any(File.class))).thenThrow(new TesseractException("decode error"));
        ReceiptOcrService service =
                new ReceiptOcrService(props) {
                    @Override
                    ITesseract newEngine() {
                        return stub;
                    }
                };

        ReceiptOcrService.OcrOutcome outcome = service.run(image);

        assertThat(outcome.status()).isEqualTo(OcrStatus.FAILED);
        assertThat(outcome.error()).contains("decode error");
    }

    @Test
    void runReturnsRetryOnTransientNativeError(@TempDir Path tmp) throws Exception {
        Path image = Files.write(tmp.resolve("locked.png"), new byte[] {0});
        ITesseract stub = mock(ITesseract.class);
        when(stub.doOCR(any(File.class))).thenThrow(new RuntimeException("file busy"));
        ReceiptOcrService service =
                new ReceiptOcrService(props) {
                    @Override
                    ITesseract newEngine() {
                        return stub;
                    }
                };

        ReceiptOcrService.OcrOutcome outcome = service.run(image);

        assertThat(outcome.status()).isEqualTo(OcrStatus.RETRY);
        assertThat(outcome.error()).contains("file busy");
    }

    @Test
    void runReturnsFailedWhenImageMissing() {
        ReceiptOcrService service = new ReceiptOcrService(props);

        ReceiptOcrService.OcrOutcome outcome =
                service.run(Paths.get("definitely-not-a-real-file-12345.png"));

        assertThat(outcome.status()).isEqualTo(OcrStatus.FAILED);
        assertThat(outcome.error()).startsWith("Image file missing");
    }

    @Test
    void tessdataAvailableFalseWhenDirectoryMissing() {
        ReceiptOcrService service = new ReceiptOcrService(props);

        assertThat(service.tessdataAvailable()).isFalse();
    }
}
