package com.fintrack.report;

import com.fintrack.asset.AssetRepository;
import com.fintrack.budget.BudgetService;
import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.dto.BudgetSummaryResponse;
import com.fintrack.common.entity.*;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.tag.TagService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final PortfolioRepository portfolioRepo;
    private final HoldingRepository holdingRepo;
    private final AssetRepository assetRepo;
    private final TransactionRepository txnRepo;
    private final IncomeCategoryRepository incomeCatRepo;
    private final ExpenseCategoryRepository expenseCatRepo;
    private final TagService tagService;
    private final BudgetService budgetService;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, Color.DARK_GRAY);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font SUMMARY_FONT =
            new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
    private static final Color HEADER_BG = new Color(30, 41, 59);
    private static final Color STRIPE_BG = new Color(241, 245, 249);

    @Transactional(readOnly = true)
    public byte[] generatePortfolioPdf(UUID userId, UUID portfolioId) {
        Portfolio portfolio =
                portfolioRepo
                        .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        List<PortfolioHolding> holdings = holdingRepo.findByPortfolioId(portfolioId);
        Map<UUID, Asset> assetsById = new HashMap<>();
        if (!holdings.isEmpty()) {
            Set<UUID> assetIds = new HashSet<>();
            holdings.forEach(h -> assetIds.add(h.getAssetId()));
            assetRepo.findAllById(assetIds).forEach(a -> assetsById.put(a.getId(), a));
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addTitle(doc, portfolio);
            addSummarySection(doc, holdings, assetsById);
            addHoldingsTable(doc, holdings, assetsById);

            doc.close();
            log.info(
                    "Portfolio PDF generated: portfolioId={} holdings={}",
                    portfolioId,
                    holdings.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for portfolio {}", portfolioId, e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateBudgetCsv(UUID userId, LocalDate from, LocalDate to) {
        List<BudgetTransaction> txns =
                txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                                userId, from, to, Pageable.unpaged())
                        .getContent();

        Map<UUID, String> catNames = new HashMap<>();
        incomeCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));
        expenseCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));

        Map<UUID, List<TagService.TagSummary>> tagsByTxn =
                tagService.loadTagsForTransactions(
                        userId, txns.stream().map(BudgetTransaction::getId).toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Type,Amount,Currency,Category,Description,Recurring,Tags\n");

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (BudgetTransaction t : txns) {
            sb.append(t.getTxnDate().format(fmt)).append(',');
            sb.append(t.getTxnType()).append(',');
            sb.append(t.getAmount()).append(',');
            sb.append(t.getCurrency() != null ? t.getCurrency() : "TRY").append(',');
            sb.append(csvEscape(catNames.getOrDefault(t.getCategoryId(), ""))).append(',');
            sb.append(csvEscape(t.getDescription() != null ? t.getDescription() : "")).append(',');
            sb.append(t.isRecurring()).append(',');
            List<TagService.TagSummary> tagList = tagsByTxn.getOrDefault(t.getId(), List.of());
            sb.append(
                    tagList.isEmpty()
                            ? ""
                            : csvEscape(
                                    tagList.stream()
                                            .map(TagService.TagSummary::name)
                                            .reduce((a, b) -> a + ";" + b)
                                            .orElse("")));
            sb.append('\n');
        }

        log.info(
                "Budget CSV generated: userId={} from={} to={} rows={}",
                userId,
                from,
                to,
                txns.size());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] generateBudgetXlsx(UUID userId, LocalDate from, LocalDate to) {
        List<BudgetTransaction> txns =
                txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                                userId, from, to, Pageable.unpaged())
                        .getContent();

        Map<UUID, String> catNames = new HashMap<>();
        incomeCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));
        expenseCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));

        Map<UUID, List<TagService.TagSummary>> tagsByTxn =
                tagService.loadTagsForTransactions(
                        userId, txns.stream().map(BudgetTransaction::getId).toList());

        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Transactions");

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            String[] headers = {
                "Date", "Type", "Amount", "Currency", "Category", "Description", "Recurring", "Tags"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (BudgetTransaction t : txns) {
                Row row = sheet.createRow(rowIdx++);
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(java.sql.Date.valueOf(t.getTxnDate()));
                dateCell.setCellStyle(dateStyle);
                row.createCell(1).setCellValue(t.getTxnType().name());
                Cell amountCell = row.createCell(2);
                amountCell.setCellValue(t.getAmount().doubleValue());
                amountCell.setCellStyle(moneyStyle);
                row.createCell(3).setCellValue(t.getCurrency() != null ? t.getCurrency() : "TRY");
                row.createCell(4).setCellValue(catNames.getOrDefault(t.getCategoryId(), ""));
                row.createCell(5)
                        .setCellValue(t.getDescription() != null ? t.getDescription() : "");
                row.createCell(6).setCellValue(t.isRecurring());
                List<TagService.TagSummary> tagList = tagsByTxn.getOrDefault(t.getId(), List.of());
                row.createCell(7)
                        .setCellValue(
                                tagList.stream()
                                        .map(TagService.TagSummary::name)
                                        .reduce((a, b) -> a + "; " + b)
                                        .orElse(""));
            }

            sheet.createFreezePane(0, 1);
            if (!txns.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIdx - 1, 0, headers.length - 1));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(baos);
            log.info(
                    "Budget XLSX generated: userId={} from={} to={} rows={}",
                    userId,
                    from,
                    to,
                    txns.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate XLSX for user {}", userId, e);
            throw new RuntimeException("XLSX generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateMonthlyBudgetPdf(UUID userId, YearMonth period) {
        String monthKey = period.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        BudgetSummaryResponse summary = budgetService.summary(userId, monthKey);

        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        List<BudgetTransaction> txns =
                txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                                userId, from, to, Pageable.unpaged())
                        .getContent();

        Map<UUID, String> catNames = new HashMap<>();
        incomeCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));
        expenseCatRepo
                .findByUserIdOrderByNameAsc(userId)
                .forEach(c -> catNames.put(c.getId(), c.getName()));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addBudgetTitle(doc, period);
            addBudgetKpis(doc, summary);
            addBudgetCategoryTable(doc, "Income by category", summary.incomeByCategory());
            addBudgetCategoryTable(doc, "Expense by category", summary.expenseByCategory());
            addBudgetTransactionsTable(doc, txns, catNames);

            doc.close();
            log.info(
                    "Monthly budget PDF generated: userId={} period={} txns={}",
                    userId,
                    monthKey,
                    txns.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error(
                    "Failed to generate monthly budget PDF for user {} period {}",
                    userId,
                    monthKey,
                    e);
            throw new RuntimeException("Monthly budget PDF generation failed", e);
        }
    }

    private void addBudgetTitle(Document doc, YearMonth period) throws DocumentException {
        Paragraph title = new Paragraph("Monthly Budget Report", TITLE_FONT);
        title.setAlignment(Element.ALIGN_LEFT);
        doc.add(title);

        String subtitle = period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        Paragraph sub = new Paragraph(subtitle, SUBTITLE_FONT);
        sub.setSpacingAfter(20);
        doc.add(sub);
    }

    private void addBudgetKpis(Document doc, BudgetSummaryResponse summary)
            throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        addSummaryCell(table, "Income", formatTry(summary.totalIncome()) + " TRY");
        addSummaryCell(table, "Expense", formatTry(summary.totalExpense()) + " TRY");
        addSummaryCell(table, "Net", formatTry(summary.net()) + " TRY");
        addSummaryCell(
                table,
                "Savings rate",
                summary.savingsRate().setScale(2, RoundingMode.HALF_UP) + "%");

        doc.add(table);
    }

    private void addBudgetCategoryTable(
            Document doc, String heading, List<BudgetSummaryResponse.CategoryAmount> rows)
            throws DocumentException {
        Paragraph h = new Paragraph(heading, SUMMARY_FONT);
        h.setSpacingBefore(6);
        h.setSpacingAfter(6);
        doc.add(h);

        if (rows.isEmpty()) {
            Paragraph empty = new Paragraph("No entries.", CELL_FONT);
            empty.setSpacingAfter(12);
            doc.add(empty);
            return;
        }

        PdfPTable table = new PdfPTable(new float[] {3f, 1.5f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);

        for (String header : new String[] {"Category", "Amount (TRY)", "Share"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        int i = 0;
        for (BudgetSummaryResponse.CategoryAmount r : rows) {
            Color bg = (i++ % 2 == 1) ? STRIPE_BG : Color.WHITE;
            addCell(
                    table,
                    r.categoryName() != null ? r.categoryName() : "Uncategorized",
                    bg,
                    Element.ALIGN_LEFT);
            addCell(table, formatTry(r.amount()), bg, Element.ALIGN_RIGHT);
            addCell(
                    table,
                    r.percent().setScale(1, RoundingMode.HALF_UP) + "%",
                    bg,
                    Element.ALIGN_RIGHT);
        }

        doc.add(table);
    }

    private void addBudgetTransactionsTable(
            Document doc, List<BudgetTransaction> txns, Map<UUID, String> catNames)
            throws DocumentException {
        Paragraph h = new Paragraph("Transactions", SUMMARY_FONT);
        h.setSpacingBefore(6);
        h.setSpacingAfter(6);
        doc.add(h);

        if (txns.isEmpty()) {
            doc.add(new Paragraph("No transactions for this period.", CELL_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(new float[] {1.2f, 1f, 2.5f, 2f, 1.3f});
        table.setWidthPercentage(100);

        for (String header :
                new String[] {"Date", "Type", "Description", "Category", "Amount (TRY)"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        int i = 0;
        for (BudgetTransaction t : txns) {
            Color bg = (i++ % 2 == 1) ? STRIPE_BG : Color.WHITE;
            addCell(table, t.getTxnDate().format(dateFmt), bg, Element.ALIGN_LEFT);
            addCell(table, t.getTxnType().name(), bg, Element.ALIGN_CENTER);
            addCell(
                    table,
                    t.getDescription() != null ? t.getDescription() : "",
                    bg,
                    Element.ALIGN_LEFT);
            addCell(table, catNames.getOrDefault(t.getCategoryId(), ""), bg, Element.ALIGN_LEFT);
            addCell(table, formatTry(t.getAmount()), bg, Element.ALIGN_RIGHT);
        }

        doc.add(table);
    }

    private void addTitle(Document doc, Portfolio portfolio) throws DocumentException {
        Paragraph title = new Paragraph("Portfolio Report", TITLE_FONT);
        title.setAlignment(Element.ALIGN_LEFT);
        doc.add(title);

        String subtitle =
                portfolio.getName()
                        + " ("
                        + portfolio.getPortfolioType()
                        + ") - "
                        + LocalDate.now()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        Paragraph sub = new Paragraph(subtitle, SUBTITLE_FONT);
        sub.setSpacingAfter(20);
        doc.add(sub);
    }

    private void addSummarySection(
            Document doc, List<PortfolioHolding> holdings, Map<UUID, Asset> assetsById)
            throws DocumentException {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PortfolioHolding h : holdings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null) continue;
            BigDecimal qty = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
            if (asset.getPrice() != null)
                totalValue = totalValue.add(asset.getPrice().multiply(qty));
            if (h.getAvgCostTry() != null)
                totalCost = totalCost.add(h.getAvgCostTry().multiply(qty));
        }

        BigDecimal pnl = totalValue.subtract(totalCost);
        BigDecimal pnlPct =
                totalCost.signum() > 0
                        ? pnl.divide(totalCost, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(20);

        addSummaryCell(summary, "Total Value", formatTry(totalValue));
        addSummaryCell(summary, "Total Cost", formatTry(totalCost));
        addSummaryCell(summary, "P&L", formatTry(pnl));
        addSummaryCell(summary, "P&L %", pnlPct.setScale(2, RoundingMode.HALF_UP) + "%");

        doc.add(summary);
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPadding(10);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", CELL_FONT));
        p.add(new Chunk(value, SUMMARY_FONT));
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addHoldingsTable(
            Document doc, List<PortfolioHolding> holdings, Map<UUID, Asset> assetsById)
            throws DocumentException {
        String[] headers = {
            "Symbol",
            "Name",
            "Type",
            "Qty",
            "Avg Cost (TRY)",
            "Price (TRY)",
            "Value (TRY)",
            "Cost Basis (TRY)",
            "P&L (TRY)",
            "P&L %"
        };
        float[] widths = {1f, 2f, 1.2f, 1.2f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1f};

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        int row = 0;
        for (PortfolioHolding h : holdings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null) continue;

            BigDecimal qty = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
            BigDecimal avgCost = h.getAvgCostTry();
            BigDecimal price = asset.getPrice();
            BigDecimal value = price != null ? price.multiply(qty) : null;
            BigDecimal costBasis = avgCost != null ? avgCost.multiply(qty) : null;
            BigDecimal pnl =
                    (value != null && costBasis != null) ? value.subtract(costBasis) : null;
            BigDecimal pnlPct =
                    (pnl != null && costBasis != null && costBasis.signum() > 0)
                            ? pnl.divide(costBasis, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                            : null;

            Color bg = (row % 2 == 1) ? STRIPE_BG : Color.WHITE;
            addCell(table, asset.getSymbol(), bg, Element.ALIGN_LEFT);
            addCell(table, asset.getName(), bg, Element.ALIGN_LEFT);
            addCell(table, asset.getAssetType().name(), bg, Element.ALIGN_CENTER);
            addCell(table, formatQty(qty), bg, Element.ALIGN_RIGHT);
            addCell(table, avgCost != null ? formatTry(avgCost) : "-", bg, Element.ALIGN_RIGHT);
            addCell(table, price != null ? formatTry(price) : "-", bg, Element.ALIGN_RIGHT);
            addCell(table, value != null ? formatTry(value) : "-", bg, Element.ALIGN_RIGHT);
            addCell(table, costBasis != null ? formatTry(costBasis) : "-", bg, Element.ALIGN_RIGHT);
            addCell(table, pnl != null ? formatTry(pnl) : "-", bg, Element.ALIGN_RIGHT);
            addCell(
                    table,
                    pnlPct != null ? pnlPct.setScale(2, RoundingMode.HALF_UP) + "%" : "-",
                    bg,
                    Element.ALIGN_RIGHT);
            row++;
        }

        doc.add(table);
    }

    private void addCell(PdfPTable table, String text, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private String formatTry(BigDecimal amount) {
        if (amount == null) return "-";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQty(BigDecimal qty) {
        if (qty == null) return "-";
        return qty.stripTrailingZeros().toPlainString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
