package com.fintrack.imports.bank;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.imports.bank.dto.BankCsvImportSummary;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints for the TR bank CSV statement importer. Both routes are multipart-form posts taking
 * {@code file} + {@code bank} + {@code accountId}; the {@code bank} value is parsed manually so
 * unknown values surface as a clean 400 via {@link
 * com.fintrack.common.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/import/bank-csv")
@RequiredArgsConstructor
public class BankCsvImportController {

    private final BankCsvImportService importService;

    @PostMapping("/preview")
    public ResponseEntity<BankCsvImportSummary> preview(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam("file") MultipartFile file,
            @RequestParam("bank") String bankRaw,
            @RequestParam("accountId") UUID accountId) {
        Bank bank = parseBank(bankRaw);
        return ResponseEntity.ok(importService.preview(file, bank, accountId, user.getId()));
    }

    @PostMapping("/commit")
    public ResponseEntity<BankCsvImportSummary> commit(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam("file") MultipartFile file,
            @RequestParam("bank") String bankRaw,
            @RequestParam("accountId") UUID accountId) {
        Bank bank = parseBank(bankRaw);
        return ResponseEntity.ok(importService.commit(file, bank, accountId, user.getId()));
    }

    private static Bank parseBank(String raw) {
        try {
            return Bank.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown bank: " + raw + " (valid: GARANTI, ISBANK, AKBANK)");
        }
    }
}
