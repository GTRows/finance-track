package com.fintrack.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class BankCsvImportServiceTest {

    @Mock private BankCsvParserRegistry parserRegistry;
    @Mock private BankCsvCategoryMatcher categoryMatcher;
    @Mock private BankCsvCategoryMatcher.Resolver resolver;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ExpenseCategoryRepository expenseRepo;
    @Mock private IncomeCategoryRepository incomeRepo;
    @Mock private ApplicationEventPublisher events;
    @Mock private AuditService auditService;
    @Mock private BankCsvParser stubParser;

    private BankCsvImportService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setUp() {
        service =
                new BankCsvImportService(
                        parserRegistry,
                        categoryMatcher,
                        accountRepository,
                        transactionRepository,
                        expenseRepo,
                        incomeRepo,
                        events,
                        auditService);
        account =
                Account.builder()
                        .id(accountId)
                        .userId(userId)
                        .name("Garanti Checking")
                        .accountType(Account.AccountType.BANK_CHECKING)
                        .currency("TRY")
                        .currentBalance(BigDecimal.ZERO)
                        .archived(false)
                        .build();
        lenient()
                .when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(Optional.of(account));
        lenient().when(parserRegistry.get(Bank.GARANTI)).thenReturn(stubParser);
        lenient().when(stubParser.charset()).thenReturn(Charset.forName("UTF-8"));
        lenient().when(stubParser.bank()).thenReturn(Bank.GARANTI);
        lenient().when(categoryMatcher.resolverFor(userId)).thenReturn(resolver);
        lenient().when(resolver.resolve(anyString())).thenReturn(null);
        lenient()
                .when(transactionRepository.findFingerprintsByAccountId(accountId))
                .thenReturn(new HashSet<>());
        lenient().when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        lenient().when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "g.csv", "text/csv", "ignored".getBytes());
    }

    private RawBankRow income(int row, String desc, String amount) {
        return new RawBankRow(
                row, LocalDate.of(2025, 1, row), new BigDecimal(amount), null, desc, desc, null);
    }

    private RawBankRow expense(int row, String desc, String amount) {
        return new RawBankRow(
                row,
                LocalDate.of(2025, 1, row),
                new BigDecimal(amount).negate(),
                null,
                desc,
                desc,
                null);
    }

    private RawBankRow warningRow(int row, String warn) {
        return new RawBankRow(row, null, null, null, "x", "x", warn);
    }

    @Test
    void preview_returnsAllRows_doesNotPersist() {
        when(stubParser.parse(any()))
                .thenReturn(
                        List.of(
                                income(2, "MAAS", "1000"),
                                expense(3, "RENT", "500"),
                                warningRow(4, "Unparseable date")));
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.totalRows()).isEqualTo(3);
        assertThat(summary.importedRows()).isZero();
        assertThat(summary.warningRows()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void preview_marksDuplicateRows() {
        RawBankRow rr = expense(2, "RENT", "500");
        String fp =
                BankCsvImportService.fingerprint(
                        accountId, rr.date(), rr.signedAmount(), null, rr.description());
        Set<String> existing = new HashSet<>();
        existing.add(fp);
        when(transactionRepository.findFingerprintsByAccountId(accountId)).thenReturn(existing);
        when(stubParser.parse(any())).thenReturn(List.of(rr));
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.duplicateRows()).isEqualTo(1);
        assertThat(summary.rows().get(0).duplicate()).isTrue();
    }

    @Test
    void preview_marksWarningRows() {
        when(stubParser.parse(any())).thenReturn(List.of(warningRow(2, "Unparseable date")));
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.warningRows()).isEqualTo(1);
        assertThat(summary.rows().get(0).warning()).isEqualTo("Unparseable date");
    }

    @Test
    void preview_resolvesCategoryFromMatcher() {
        UUID catId = UUID.randomUUID();
        when(stubParser.parse(any())).thenReturn(List.of(expense(2, "NETFLIX", "100")));
        when(resolver.resolve("NETFLIX")).thenReturn(catId);
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.rows().get(0).matchedCategoryId()).isEqualTo(catId);
    }

    @Test
    void commit_persistsValidRowsOnly() {
        when(stubParser.parse(any()))
                .thenReturn(
                        List.of(
                                income(2, "MAAS", "1000"),
                                expense(3, "RENT", "500"),
                                warningRow(4, "Bad row")));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BankCsvImportSummary summary = service.commit(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.importedRows()).isEqualTo(2);
        assertThat(summary.skippedRows()).isEqualTo(1);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void commit_skipsWarningRows() {
        when(stubParser.parse(any())).thenReturn(List.of(warningRow(2, "Bad")));
        BankCsvImportSummary summary = service.commit(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.importedRows()).isZero();
        assertThat(summary.skippedRows()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void commit_skipsDuplicateRows() {
        RawBankRow rr = expense(2, "RENT", "500");
        String fp =
                BankCsvImportService.fingerprint(
                        accountId, rr.date(), rr.signedAmount(), null, rr.description());
        Set<String> existing = new HashSet<>();
        existing.add(fp);
        when(transactionRepository.findFingerprintsByAccountId(accountId)).thenReturn(existing);
        when(stubParser.parse(any())).thenReturn(List.of(rr));
        BankCsvImportSummary summary = service.commit(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.importedRows()).isZero();
        assertThat(summary.skippedRows()).isEqualTo(1);
        assertThat(summary.duplicateRows()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void commit_publishesEventPerImportedRow() {
        when(stubParser.parse(any()))
                .thenReturn(List.of(income(2, "MAAS", "1000"), expense(3, "RENT", "500")));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.commit(file(), Bank.GARANTI, accountId, userId);
        verify(events, times(2)).publishEvent(any(BudgetTransactionPersistedEvent.class));
    }

    @Test
    void commit_stampsAccountIdOnEveryEvent() {
        when(stubParser.parse(any())).thenReturn(List.of(income(2, "MAAS", "1000")));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.commit(file(), Bank.GARANTI, accountId, userId);
        ArgumentCaptor<BudgetTransactionPersistedEvent> cap =
                ArgumentCaptor.forClass(BudgetTransactionPersistedEvent.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().accountId()).isEqualTo(accountId);
        assertThat(cap.getValue().previousAccountId()).isNull();
    }

    @Test
    void commit_stampsAccountCurrency() {
        account.setCurrency("USD");
        when(stubParser.parse(any())).thenReturn(List.of(income(2, "PAY", "1000")));
        ArgumentCaptor<BudgetTransaction> cap = ArgumentCaptor.forClass(BudgetTransaction.class);
        when(transactionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));
        service.commit(file(), Bank.GARANTI, accountId, userId);
        assertThat(cap.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void commit_handlesDataIntegrityViolationOnRace_incrementsSkipped() {
        when(stubParser.parse(any())).thenReturn(List.of(expense(2, "RENT", "500")));
        when(transactionRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("race"));
        BankCsvImportSummary summary = service.commit(file(), Bank.GARANTI, accountId, userId);
        assertThat(summary.importedRows()).isZero();
        assertThat(summary.skippedRows()).isEqualTo(1);
        assertThat(summary.duplicateRows()).isEqualTo(1);
    }

    @Test
    void preview_throwsBusinessRule_onParserFailure() {
        when(stubParser.parse(any())).thenThrow(new IllegalArgumentException("File is empty"));
        assertThatThrownBy(() -> service.preview(file(), Bank.GARANTI, accountId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid CSV");
        verify(auditService, atLeastOnce())
                .failure(eq(AuditAction.BANK_CSV_FAILED), eq(userId), any(), anyString());
    }

    @Test
    void commit_throwsBusinessRule_whenAccountNotOwned() {
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.commit(file(), Bank.GARANTI, accountId, userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void commit_emitsAuditSuccess() {
        when(stubParser.parse(any())).thenReturn(List.of(income(2, "PAY", "100")));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.commit(file(), Bank.GARANTI, accountId, userId);
        verify(auditService, atLeast(1))
                .success(eq(AuditAction.BANK_CSV_COMMITTED), eq(userId), any(), anyString());
    }

    @Test
    void preview_emitsAuditSuccess() {
        when(stubParser.parse(any())).thenReturn(List.of(income(2, "PAY", "100")));
        service.preview(file(), Bank.GARANTI, accountId, userId);
        verify(auditService, atLeast(1))
                .success(eq(AuditAction.BANK_CSV_PREVIEWED), eq(userId), any(), anyString());
    }

    @Test
    void commit_failsAccountGuard_emitsAuditFailure() {
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.commit(file(), Bank.GARANTI, accountId, userId))
                .isInstanceOf(BusinessRuleException.class);
        verify(auditService, atLeastOnce())
                .failure(eq(AuditAction.BANK_CSV_FAILED), eq(userId), any(), anyString());
    }

    @Test
    void fingerprint_isStable_acrossInvocations() {
        UUID acc = UUID.randomUUID();
        LocalDate date = LocalDate.of(2025, 1, 2);
        BigDecimal amount = new BigDecimal("-1234.56");
        String a = BankCsvImportService.fingerprint(acc, date, amount, null, "RENT");
        String b = BankCsvImportService.fingerprint(acc, date, amount, null, "RENT");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    void fingerprint_differs_onAccountIdChange() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        LocalDate date = LocalDate.of(2025, 1, 2);
        BigDecimal amount = new BigDecimal("-1234.56");
        String fa = BankCsvImportService.fingerprint(a, date, amount, null, "RENT");
        String fb = BankCsvImportService.fingerprint(b, date, amount, null, "RENT");
        assertThat(fa).isNotEqualTo(fb);
    }

    @Test
    void preview_doesNotMutateExistingFingerprints() {
        when(stubParser.parse(any())).thenReturn(List.of(income(2, "PAY", "100")));
        Set<String> existing = new HashSet<>();
        when(transactionRepository.findFingerprintsByAccountId(accountId)).thenReturn(existing);
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        // Even on preview, existing fingerprints set is local; preview should not save.
        assertThat(summary.importedRows()).isZero();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void preview_carriesFingerprintBack() {
        when(stubParser.parse(any())).thenReturn(List.of(expense(2, "RENT", "500")));
        BankCsvImportSummary summary = service.preview(file(), Bank.GARANTI, accountId, userId);
        BankCsvPreviewRow row = summary.rows().get(0);
        assertThat(row.fingerprint()).isNotNull();
        assertThat(row.fingerprint()).hasSize(64);
        assertThat(row.inferredType()).isEqualTo(BudgetTransaction.TxnType.EXPENSE);
        assertThat(row.amount()).isEqualByComparingTo("500");
    }
}
