package com.fintrack.imports.bank;

import com.fintrack.account.AccountRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.event.BudgetTransactionPersistedEvent;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.imports.bank.dto.BankCsvImportSummary;
import com.fintrack.imports.bank.dto.BankCsvPreviewRow;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bank CSV preview + commit service. Mirrors the {@code ExcelImportService.preview / commit} shape:
 * preview is read-only and advisory; commit re-parses the canonical file from scratch and persists
 * every clean, non-duplicate row in a single transaction. A SHA-256 fingerprint over {@code
 * (accountId, date, signedAmount, balanceAfter, description)} dedupes re-uploads, with a partial
 * unique index on {@code transactions(account_id, import_fingerprint)} as the load-bearing
 * race-safety guard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankCsvImportService {

    private final BankCsvParserRegistry parserRegistry;
    private final BankCsvCategoryMatcher categoryMatcher;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseCategoryRepository expenseRepo;
    private final IncomeCategoryRepository incomeRepo;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public BankCsvImportSummary preview(
            MultipartFile file, Bank bank, UUID accountId, UUID userId) {
        return run(file, bank, accountId, userId, false);
    }

    @Transactional
    public BankCsvImportSummary commit(MultipartFile file, Bank bank, UUID accountId, UUID userId) {
        return run(file, bank, accountId, userId, true);
    }

    private BankCsvImportSummary run(
            MultipartFile file, Bank bank, UUID accountId, UUID userId, boolean commit) {
        Account account = requireOwnedAccount(accountId, userId);
        BankCsvParser parser = parserRegistry.get(bank);
        List<RawBankRow> raw;
        try (InputStream is = file.getInputStream()) {
            raw = parser.parse(is);
        } catch (IllegalArgumentException | IOException e) {
            auditService.failure(
                    AuditAction.BANK_CSV_FAILED,
                    userId,
                    currentUsername(),
                    "bank=" + bank + " account=" + accountId + " err=" + e.getMessage());
            throw new BusinessRuleException("Invalid CSV: " + e.getMessage(), "BANK_CSV_INVALID");
        }
        BankCsvCategoryMatcher.Resolver resolver = categoryMatcher.resolverFor(userId);
        Set<String> existing = transactionRepository.findFingerprintsByAccountId(accountId);
        Map<UUID, String> categoryNames = loadCategoryNames(userId);

        List<BankCsvPreviewRow> rows = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int duplicates = 0;
        int warnings = 0;

        for (RawBankRow rr : raw) {
            String warning = rr.warning();
            BigDecimal signed = rr.signedAmount();
            BudgetTransaction.TxnType type = null;
            BigDecimal amount = null;
            String fingerprint = null;
            boolean duplicate = false;
            UUID matchedCat = null;
            if (warning == null && (signed == null || rr.date() == null)) {
                warning = "Missing date or amount";
            }
            if (warning == null) {
                type =
                        signed.signum() >= 0
                                ? BudgetTransaction.TxnType.INCOME
                                : BudgetTransaction.TxnType.EXPENSE;
                amount = signed.abs();
                fingerprint =
                        fingerprint(
                                accountId, rr.date(), signed, rr.balanceAfter(), rr.description());
                duplicate = existing.contains(fingerprint);
                matchedCat = resolver.resolve(rr.description());
            }
            if (warning != null) {
                warnings++;
            }
            if (duplicate) {
                duplicates++;
            }
            String catName = matchedCat != null ? categoryNames.get(matchedCat) : null;

            BankCsvPreviewRow previewRow =
                    new BankCsvPreviewRow(
                            rr.rowNumber(),
                            rr.date(),
                            type,
                            amount,
                            rr.description(),
                            rr.counterparty(),
                            matchedCat,
                            catName,
                            fingerprint,
                            warning,
                            duplicate);
            rows.add(previewRow);

            if (!commit || warning != null || duplicate || amount == null) {
                if (commit && (warning != null || duplicate)) {
                    skipped++;
                }
                continue;
            }

            BudgetTransaction txn =
                    BudgetTransaction.builder()
                            .userId(userId)
                            .accountId(accountId)
                            .txnType(type)
                            .amount(amount)
                            .currency(account.getCurrency())
                            .categoryId(matchedCat)
                            .description(rr.description())
                            .txnDate(rr.date())
                            .importFingerprint(fingerprint)
                            .build();
            try {
                BudgetTransaction saved = transactionRepository.save(txn);
                existing.add(fingerprint);
                events.publishEvent(
                        new BudgetTransactionPersistedEvent(
                                userId,
                                saved.getId(),
                                saved.getTxnType(),
                                saved.getCategoryId(),
                                saved.getAmount(),
                                saved.getTxnDate(),
                                accountId,
                                null));
                imported++;
            } catch (DataIntegrityViolationException dup) {
                skipped++;
                duplicates++;
                log.debug("Bank CSV row skipped on race-lost dedupe: fingerprint={}", fingerprint);
            }
        }

        BankCsvImportSummary summary =
                new BankCsvImportSummary(
                        rows.size(), imported, skipped, duplicates, warnings, rows);

        String detail =
                String.format(
                        Locale.ROOT,
                        "bank=%s account=%s rows=%d imported=%d skipped=%d duplicates=%d"
                                + " warnings=%d",
                        bank,
                        accountId,
                        summary.totalRows(),
                        summary.importedRows(),
                        summary.skippedRows(),
                        summary.duplicateRows(),
                        summary.warningRows());
        auditService.success(
                commit ? AuditAction.BANK_CSV_COMMITTED : AuditAction.BANK_CSV_PREVIEWED,
                userId,
                currentUsername(),
                detail);
        return summary;
    }

    private Account requireOwnedAccount(UUID accountId, UUID userId) {
        return accountRepository
                .findByIdAndUserIdAndArchivedFalse(accountId, userId)
                .orElseThrow(
                        () -> {
                            auditService.failure(
                                    AuditAction.BANK_CSV_FAILED,
                                    userId,
                                    currentUsername(),
                                    "ACCOUNT_NOT_OWNED accountId=" + accountId);
                            return new BusinessRuleException(
                                    "Account not found", "ACCOUNT_NOT_OWNED");
                        });
    }

    private Map<UUID, String> loadCategoryNames(UUID userId) {
        Map<UUID, String> map = new HashMap<>();
        expenseRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), c.getName()));
        incomeRepo.findByUserIdOrderByNameAsc(userId).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }

    static String fingerprint(
            UUID accountId,
            LocalDate date,
            BigDecimal signedAmount,
            BigDecimal balanceAfter,
            String description) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder seed = new StringBuilder(128);
            seed.append(accountId);
            seed.append('|');
            seed.append(date == null ? "" : date.toString());
            seed.append('|');
            seed.append(
                    signedAmount == null ? "" : signedAmount.stripTrailingZeros().toPlainString());
            seed.append('|');
            seed.append(
                    balanceAfter == null ? "" : balanceAfter.stripTrailingZeros().toPlainString());
            seed.append('|');
            String desc = description == null ? "" : description.toLowerCase(Locale.ROOT).trim();
            seed.append(desc);
            byte[] digest = md.digest(seed.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
