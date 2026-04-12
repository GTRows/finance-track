package com.fintrack.report;

import com.fintrack.asset.AssetRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.common.entity.*;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, Color.DARK_GRAY);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font SUMMARY_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
    private static final Color HEADER_BG = new Color(30, 41, 59);
    private static final Color STRIPE_BG = new Color(241, 245, 249);

    @Transactional(readOnly = true)
    public byte[] generatePortfolioPdf(UUID userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId)
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
            log.info("Portfolio PDF generated: portfolioId={} holdings={}", portfolioId, holdings.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for portfolio {}", portfolioId, e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateBudgetCsv(UUID userId, LocalDate from, LocalDate to) {
        List<BudgetTransaction> txns = txnRepo
                .findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(userId, from, to, Pageable.unpaged())
                .getContent();

        Map<UUID, String> catNames = new HashMap<>();
        incomeCatRepo.findByUserIdOrderByNameAsc(userId).forEach(c -> catNames.put(c.getId(), c.getName()));
        expenseCatRepo.findByUserIdOrderByNameAsc(userId).forEach(c -> catNames.put(c.getId(), c.getName()));

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
            sb.append(t.getTags() != null ? csvEscape(String.join(";", t.getTags())) : "");
            sb.append('\n');
        }

        log.info("Budget CSV generated: userId={} from={} to={} rows={}", userId, from, to, txns.size());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void addTitle(Document doc, Portfolio portfolio) throws DocumentException {
        Paragraph title = new Paragraph("Portfolio Report", TITLE_FONT);
        title.setAlignment(Element.ALIGN_LEFT);
        doc.add(title);

        String subtitle = portfolio.getName() + " (" + portfolio.getPortfolioType() + ") - "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        Paragraph sub = new Paragraph(subtitle, SUBTITLE_FONT);
        sub.setSpacingAfter(20);
        doc.add(sub);
    }

    private void addSummarySection(Document doc, List<PortfolioHolding> holdings, Map<UUID, Asset> assetsById)
            throws DocumentException {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PortfolioHolding h : holdings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null) continue;
            BigDecimal qty = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
            if (asset.getPrice() != null) totalValue = totalValue.add(asset.getPrice().multiply(qty));
            if (h.getAvgCostTry() != null) totalCost = totalCost.add(h.getAvgCostTry().multiply(qty));
        }

        BigDecimal pnl = totalValue.subtract(totalCost);
        BigDecimal pnlPct = totalCost.signum() > 0
                ? pnl.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
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

    private void addHoldingsTable(Document doc, List<PortfolioHolding> holdings, Map<UUID, Asset> assetsById)
            throws DocumentException {
        String[] headers = {"Symbol", "Name", "Type", "Qty", "Avg Cost (TRY)", "Price (TRY)",
                "Value (TRY)", "Cost Basis (TRY)", "P&L (TRY)", "P&L %"};
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
            BigDecimal pnl = (value != null && costBasis != null) ? value.subtract(costBasis) : null;
            BigDecimal pnlPct = (pnl != null && costBasis != null && costBasis.signum() > 0)
                    ? pnl.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
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
            addCell(table, pnlPct != null ? pnlPct.setScale(2, RoundingMode.HALF_UP) + "%" : "-", bg, Element.ALIGN_RIGHT);
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
