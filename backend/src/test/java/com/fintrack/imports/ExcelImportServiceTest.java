package com.fintrack.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.Portfolio.PortfolioType;
import com.fintrack.imports.dto.ImportPreviewRow;
import com.fintrack.imports.dto.ImportSummary;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock PortfolioRepository portfolioRepo;
    @Mock AssetRepository assetRepo;
    @Mock InvestmentTransactionRepository txnRepo;

    @InjectMocks ExcelImportService service;

    private final UUID userId = UUID.randomUUID();

    private static class Builder {
        private final Workbook wb = new XSSFWorkbook();
        private final Sheet sheet = wb.createSheet("YATIRIM_LOG");
        private int cursor = 4;

        Builder() {
            // Header rows 0-3 are ignored by the parser. Stamp a header row at index 3 for realism.
            Row header = sheet.createRow(3);
            header.createCell(1).setCellValue("Date");
            header.createCell(2).setCellValue("Type");
            header.createCell(3).setCellValue("Asset");
            header.createCell(4).setCellValue("Amount");
            header.createCell(5).setCellValue("Note");
        }

        Builder row(LocalDate date, String type, String asset, String amount, String note) {
            Row row = sheet.createRow(cursor++);
            if (date != null) {
                org.apache.poi.ss.usermodel.CellStyle style = wb.createCellStyle();
                org.apache.poi.ss.usermodel.DataFormat fmt = wb.createDataFormat();
                style.setDataFormat(fmt.getFormat("yyyy-mm-dd"));
                org.apache.poi.ss.usermodel.Cell c = row.createCell(1);
                c.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                c.setCellStyle(style);
            }
            if (type != null) row.createCell(2).setCellValue(type);
            if (asset != null) row.createCell(3).setCellValue(asset);
            if (amount != null) row.createCell(4).setCellValue(Double.parseDouble(amount));
            if (note != null) row.createCell(5).setCellValue(note);
            return this;
        }

        Builder emptyRow() {
            sheet.createRow(cursor++);
            return this;
        }

        byte[] bytes() throws IOException {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                wb.close();
                return out.toByteArray();
            }
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "book.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    private Asset asset(String symbol) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(AssetType.FUND)
                .currency("TRY")
                .build();
    }

    private Portfolio portfolio(String name, PortfolioType type) {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .portfolioType(type)
                .active(true)
                .build();
    }

    @Test
    void parseThrowsOnMissingSheet() throws IOException {
        Workbook wb = new XSSFWorkbook();
        wb.createSheet("other");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        assertThatThrownBy(() -> service.preview(file(out.toByteArray())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YATIRIM_LOG");
    }

    @Test
    void previewMapsSupportedTypesAndFlagsUnknown() throws IOException {
        Asset tta = asset("TTA");
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        byte[] bytes =
                new Builder()
                        .row(LocalDate.of(2026, 4, 1), "Yatirim", "TTA", "1000", "apr")
                        .row(LocalDate.of(2026, 4, 2), "Kar Alma", "TTA", "500", null)
                        .row(LocalDate.of(2026, 4, 3), "Bilinmeyen", "TTA", "100", null)
                        .bytes();

        ImportSummary res = service.preview(file(bytes));

        assertThat(res.totalRows()).isEqualTo(3);
        assertThat(res.importedRows()).isZero();
        assertThat(res.warningRows()).isEqualTo(1);
        List<ImportPreviewRow> rows = res.rows();
        assertThat(rows.get(0).mappedType()).isEqualTo(InvestmentTransaction.TxnType.BUY);
        assertThat(rows.get(1).mappedType()).isEqualTo(InvestmentTransaction.TxnType.SELL);
        assertThat(rows.get(2).warning()).isEqualTo("Unknown transaction type");
    }

    @Test
    void previewFlagsMissingFields() throws IOException {
        Asset tta = asset("TTA");
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        byte[] bytes =
                new Builder()
                        .row(null, "Yatirim", "TTA", "100", null)
                        .row(LocalDate.of(2026, 4, 2), "Yatirim", null, "100", null)
                        .row(LocalDate.of(2026, 4, 3), "Yatirim", "TTA", "0", null)
                        .row(LocalDate.of(2026, 4, 4), "Yatirim", "UNKNOWN", "100", null)
                        .bytes();

        ImportSummary res = service.preview(file(bytes));

        assertThat(res.rows()).hasSize(4);
        assertThat(res.rows().get(0).warning()).contains("date");
        assertThat(res.rows().get(1).warning()).contains("asset");
        assertThat(res.rows().get(2).warning()).contains("amount");
        assertThat(res.rows().get(3).warning()).contains("Asset not in catalog");
        assertThat(res.warningRows()).isEqualTo(4);
    }

    @Test
    void previewSkipsFullyBlankRows() throws IOException {
        when(assetRepo.findAll()).thenReturn(List.of());
        byte[] bytes = new Builder().emptyRow().emptyRow().bytes();

        ImportSummary res = service.preview(file(bytes));

        assertThat(res.totalRows()).isZero();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void previewNormalizesTurkishCharactersInType() throws IOException {
        Asset tta = asset("TTA");
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        byte[] bytes =
                new Builder()
                        .row(LocalDate.of(2026, 4, 1), "BES KATKI", "TTA", "500", null)
                        .row(LocalDate.of(2026, 4, 2), "Para Çekme", "TTA", "200", null)
                        .row(LocalDate.of(2026, 4, 3), "Fon Değişikliği", "TTA", "100", null)
                        .bytes();

        ImportSummary res = service.preview(file(bytes));

        assertThat(res.rows().get(0).mappedType())
                .isEqualTo(InvestmentTransaction.TxnType.BES_CONTRIBUTION);
        assertThat(res.rows().get(1).mappedType())
                .isEqualTo(InvestmentTransaction.TxnType.WITHDRAW);
        assertThat(res.rows().get(2).mappedType())
                .isEqualTo(InvestmentTransaction.TxnType.REBALANCE);
    }

    @Test
    void commitCreatesMissingPortfoliosAndPersistsTransactions() throws IOException {
        Asset tta = asset("TTA");
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(portfolioRepo.save(any(Portfolio.class)))
                .thenAnswer(
                        inv -> {
                            Portfolio p = inv.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });
        when(txnRepo.findByPortfolioIdInAndNotesStartingWith(any(), any())).thenReturn(List.of());

        byte[] bytes =
                new Builder()
                        .row(LocalDate.of(2026, 4, 1), "Yatirim", "TTA", "1000", "apr")
                        .row(LocalDate.of(2026, 4, 2), "BES KATKI", "TTA", "500", null)
                        .bytes();

        ImportSummary res = service.commit(userId, file(bytes));

        assertThat(res.importedRows()).isEqualTo(2);
        verify(portfolioRepo, times(2)).save(any(Portfolio.class));
        verify(txnRepo, times(2)).save(any(InvestmentTransaction.class));
    }

    @Test
    void commitReusesExistingPortfoliosByName() throws IOException {
        Asset tta = asset("TTA");
        Portfolio bireysel = portfolio("Bireysel", PortfolioType.INDIVIDUAL);
        Portfolio bes = portfolio("BES", PortfolioType.BES);
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(bireysel, bes));
        when(txnRepo.findByPortfolioIdInAndNotesStartingWith(any(), any())).thenReturn(List.of());

        byte[] bytes =
                new Builder().row(LocalDate.of(2026, 4, 1), "Yatirim", "TTA", "1000", null).bytes();

        service.commit(userId, file(bytes));

        verify(portfolioRepo, never()).save(any(Portfolio.class));
    }

    @Test
    void commitSkipsDuplicateFingerprints() throws IOException {
        Asset tta = asset("TTA");
        Portfolio bireysel = portfolio("Bireysel", PortfolioType.INDIVIDUAL);
        Portfolio bes = portfolio("BES", PortfolioType.BES);
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(bireysel, bes));
        InvestmentTransaction existing =
                InvestmentTransaction.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(bireysel.getId())
                        .assetId(tta.getId())
                        .txnType(InvestmentTransaction.TxnType.BUY)
                        .amountTry(new BigDecimal("1000"))
                        .txnDate(LocalDate.of(2026, 4, 1))
                        .notes("[xlsx-import] apr")
                        .build();
        when(txnRepo.findByPortfolioIdInAndNotesStartingWith(any(), any()))
                .thenReturn(List.of(existing));

        byte[] bytes =
                new Builder()
                        .row(LocalDate.of(2026, 4, 1), "Yatirim", "TTA", "1000", "apr")
                        .bytes();

        ImportSummary res = service.commit(userId, file(bytes));

        assertThat(res.importedRows()).isZero();
        assertThat(res.skippedRows()).isEqualTo(1);
        verify(txnRepo, never()).save(any(InvestmentTransaction.class));
    }

    @Test
    void commitCountsWarningRowsAsSkipped() throws IOException {
        Asset tta = asset("TTA");
        Portfolio bireysel = portfolio("Bireysel", PortfolioType.INDIVIDUAL);
        Portfolio bes = portfolio("BES", PortfolioType.BES);
        when(assetRepo.findAll()).thenReturn(List.of(tta));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(bireysel, bes));
        when(txnRepo.findByPortfolioIdInAndNotesStartingWith(any(), any())).thenReturn(List.of());

        byte[] bytes =
                new Builder()
                        .row(LocalDate.of(2026, 4, 1), "Bilinmeyen", "TTA", "1000", null)
                        .bytes();

        ImportSummary res = service.commit(userId, file(bytes));

        assertThat(res.importedRows()).isZero();
        assertThat(res.skippedRows()).isEqualTo(1);
        assertThat(res.warningRows()).isEqualTo(1);
    }

    @Test
    void previewWrapsCorruptFileInIllegalArgument() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "garbage.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> service.preview(file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
