package com.fintrack.report;

import com.fintrack.asset.AssetRepository;
import com.fintrack.budget.BudgetService;
import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.dto.BudgetSummaryResponse;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.Portfolio.PortfolioType;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.tag.TagService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock PortfolioRepository portfolioRepo;
    @Mock HoldingRepository holdingRepo;
    @Mock AssetRepository assetRepo;
    @Mock TransactionRepository txnRepo;
    @Mock IncomeCategoryRepository incomeCatRepo;
    @Mock ExpenseCategoryRepository expenseCatRepo;
    @Mock TagService tagService;
    @Mock BudgetService budgetService;

    @InjectMocks ReportService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private Asset asset(String symbol, String price) {
        return Asset.builder()
                .id(UUID.randomUUID()).symbol(symbol).name(symbol)
                .assetType(AssetType.CRYPTO).currency("TRY")
                .price(new BigDecimal(price)).build();
    }

    private PortfolioHolding holding(UUID assetId, String qty, String cost) {
        return PortfolioHolding.builder()
                .id(UUID.randomUUID()).portfolioId(portfolioId).assetId(assetId)
                .quantity(new BigDecimal(qty)).avgCostTry(new BigDecimal(cost)).build();
    }

    private BudgetTransaction txn(TxnType type, String amount, LocalDate date, String description) {
        return BudgetTransaction.builder()
                .id(UUID.randomUUID()).userId(userId)
                .txnType(type).amount(new BigDecimal(amount))
                .txnDate(date).description(description).build();
    }

    @Test
    void generatePortfolioPdfThrowsWhenNotOwned() {
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generatePortfolioPdf(userId, portfolioId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generatePortfolioPdfReturnsValidPdfBytesWithHoldings() {
        Portfolio p = Portfolio.builder().id(portfolioId).userId(userId)
                .name("Main").portfolioType(PortfolioType.INDIVIDUAL).active(true).build();
        Asset btc = asset("BTC", "100");
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId)).thenReturn(Optional.of(p));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of(holding(btc.getId(), "2", "80")));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc));

        byte[] pdf = service.generatePortfolioPdf(userId, portfolioId);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generatePortfolioPdfWithEmptyHoldings() {
        Portfolio p = Portfolio.builder().id(portfolioId).userId(userId)
                .name("Empty").portfolioType(PortfolioType.CRYPTO).active(true).build();
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId)).thenReturn(Optional.of(p));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of());

        byte[] pdf = service.generatePortfolioPdf(userId, portfolioId);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateBudgetCsvProducesHeaderAndRows() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);
        BudgetTransaction t = txn(TxnType.EXPENSE, "150", LocalDate.of(2026, 4, 15), "Food \"quoted\"");
        UUID catId = UUID.randomUUID();
        t.setCategoryId(catId);
        Page<BudgetTransaction> page = new PageImpl<>(List.of(t));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                eq(userId), eq(from), eq(to), any(Pageable.class))).thenReturn(page);
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(
                ExpenseCategory.builder().id(catId).userId(userId).name("Food").build()));
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(Map.of());

        String csv = new String(service.generateBudgetCsv(userId, from, to));

        assertThat(csv.lines().findFirst()).contains("Date,Type,Amount,Currency,Category,Description,Recurring,Tags");
        assertThat(csv).contains("2026-04-15").contains("EXPENSE").contains("150");
        assertThat(csv).contains("\"Food \"\"quoted\"\"\"");
    }

    @Test
    void generateBudgetCsvEmptyResultStillReturnsHeader() {
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(Map.of());

        String csv = new String(service.generateBudgetCsv(userId, LocalDate.now(), LocalDate.now()));

        assertThat(csv).isEqualTo("Date,Type,Amount,Currency,Category,Description,Recurring,Tags\n");
    }

    @Test
    void generateBudgetCsvIncludesTagsJoinedBySemicolon() {
        BudgetTransaction t = txn(TxnType.EXPENSE, "10", LocalDate.of(2026, 4, 1), "x");
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(Map.of(
                t.getId(), List.of(
                        new TagService.TagSummary(UUID.randomUUID(), "food", "#f00"),
                        new TagService.TagSummary(UUID.randomUUID(), "travel", "#0f0"))));

        String csv = new String(service.generateBudgetCsv(userId, LocalDate.now(), LocalDate.now()));

        assertThat(csv).contains("food;travel");
    }

    @Test
    void generateBudgetXlsxWritesHeaderRowAndDataRows() throws Exception {
        BudgetTransaction t = txn(TxnType.EXPENSE, "150", LocalDate.of(2026, 4, 15), "Food");
        UUID catId = UUID.randomUUID();
        t.setCategoryId(catId);
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(
                ExpenseCategory.builder().id(catId).userId(userId).name("Food").build()));
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(Map.of());

        byte[] xlsx = service.generateBudgetXlsx(userId, LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Transactions");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Amount");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("EXPENSE");
            assertThat(dataRow.getCell(2).getNumericCellValue()).isEqualTo(150.0);
            assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("Food");
        }
    }

    @Test
    void generateBudgetXlsxWithNoRowsStillIncludesHeader() throws Exception {
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(Map.of());

        byte[] xlsx = service.generateBudgetXlsx(userId, LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(sheet.getLastRowNum()).isZero();
        }
    }

    @Test
    void generateMonthlyBudgetPdfReturnsValidPdf() {
        YearMonth period = YearMonth.of(2026, 4);
        BudgetSummaryResponse summary = new BudgetSummaryResponse(
                "2026-04",
                new BigDecimal("10000"),
                new BigDecimal("5000"),
                new BigDecimal("5000"),
                new BigDecimal("50.00"),
                List.of(new BudgetSummaryResponse.CategoryAmount(
                        UUID.randomUUID(), "Salary", "#0f0",
                        new BigDecimal("10000"), new BigDecimal("100"), null, null, null)),
                List.of(new BudgetSummaryResponse.CategoryAmount(
                        UUID.randomUUID(), "Food", "#f00",
                        new BigDecimal("5000"), new BigDecimal("100"), null, null, null)));
        when(budgetService.summary(userId, "2026-04")).thenReturn(summary);
        BudgetTransaction t = txn(TxnType.INCOME, "10000", LocalDate.of(2026, 4, 1), "Salary");
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(
                IncomeCategory.builder().id(UUID.randomUUID()).userId(userId).name("Salary").build()));
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        byte[] pdf = service.generateMonthlyBudgetPdf(userId, period);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateMonthlyBudgetPdfHandlesEmptyCategoriesAndTransactions() {
        YearMonth period = YearMonth.of(2026, 4);
        BudgetSummaryResponse summary = new BudgetSummaryResponse(
                "2026-04", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of());
        when(budgetService.summary(userId, "2026-04")).thenReturn(summary);
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        byte[] pdf = service.generateMonthlyBudgetPdf(userId, period);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void portfolioPdfSkipsHoldingsWithMissingAssets() {
        Portfolio p = Portfolio.builder().id(portfolioId).userId(userId)
                .name("Main").portfolioType(PortfolioType.INDIVIDUAL).active(true).build();
        Asset btc = asset("BTC", "100");
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId)).thenReturn(Optional.of(p));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of(
                holding(btc.getId(), "1", "80"),
                holding(UUID.randomUUID(), "5", "10")));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc));

        byte[] pdf = service.generatePortfolioPdf(userId, portfolioId);

        assertThat(pdf).isNotEmpty();
    }
}
