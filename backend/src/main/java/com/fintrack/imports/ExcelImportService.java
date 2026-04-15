package com.fintrack.imports;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.imports.dto.ImportPreviewRow;
import com.fintrack.imports.dto.ImportSummary;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private static final String LOG_SHEET = "YATIRIM_LOG";
    private static final String NOTE_PREFIX = "[xlsx-import]";
    private static final int HEADER_ROW = 3;
    private static final int COL_DATE = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_ASSET = 3;
    private static final int COL_AMOUNT = 4;
    private static final int COL_NOTE = 5;

    private final PortfolioRepository portfolioRepo;
    private final AssetRepository assetRepo;
    private final InvestmentTransactionRepository txnRepo;

    @Transactional(readOnly = true)
    public ImportSummary preview(MultipartFile file) {
        return parse(file, false, null);
    }

    @Transactional
    public ImportSummary commit(UUID userId, MultipartFile file) {
        return parse(file, true, userId);
    }

    private ImportSummary parse(MultipartFile file, boolean commit, UUID userId) {
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheet(LOG_SHEET);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + LOG_SHEET);
            }

            Map<String, Asset> assetIndex = indexAssets();
            Portfolio individual = null;
            Portfolio bes = null;
            Set<String> existingFingerprints = new HashSet<>();

            if (commit) {
                individual = ensurePortfolio(userId, "Bireysel", Portfolio.PortfolioType.INDIVIDUAL);
                bes = ensurePortfolio(userId, "BES", Portfolio.PortfolioType.BES);
                existingFingerprints = loadExistingFingerprints(individual.getId(), bes.getId());
            }

            List<ImportPreviewRow> rows = new ArrayList<>();
            int imported = 0;
            int skipped = 0;
            int warnings = 0;

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                LocalDate date = readDate(row.getCell(COL_DATE));
                String type = readString(row.getCell(COL_TYPE));
                String asset = readString(row.getCell(COL_ASSET));
                BigDecimal amount = readNumber(row.getCell(COL_AMOUNT));
                String note = readString(row.getCell(COL_NOTE));

                if (date == null && type == null && asset == null && amount == null) continue;

                String warning = null;
                InvestmentTransaction.TxnType mapped = mapType(type);
                if (mapped == null) {
                    warning = "Unknown transaction type";
                } else if (date == null) {
                    warning = "Missing date";
                } else if (asset == null || asset.isBlank()) {
                    warning = "Missing asset";
                } else if (amount == null || amount.signum() <= 0) {
                    warning = "Missing or non-positive amount";
                }

                Asset assetEntity = asset != null ? assetIndex.get(asset.toUpperCase(Locale.ROOT)) : null;
                if (warning == null && assetEntity == null) {
                    warning = "Asset not in catalog: " + asset;
                }

                if (warning != null) warnings++;

                ImportPreviewRow previewRow = new ImportPreviewRow(
                        r + 1, date, type, mapped, asset, amount, note, warning
                );
                rows.add(previewRow);

                if (!commit || warning != null || mapped == null || assetEntity == null
                        || date == null || amount == null) {
                    if (commit && warning != null) skipped++;
                    continue;
                }

                Portfolio target = mapped == InvestmentTransaction.TxnType.BES_CONTRIBUTION ? bes : individual;
                String fingerprint = fingerprint(target.getId(), assetEntity.getId(), mapped, date, amount);
                if (existingFingerprints.contains(fingerprint)) {
                    skipped++;
                    continue;
                }

                InvestmentTransaction txn = InvestmentTransaction.builder()
                        .portfolioId(target.getId())
                        .assetId(assetEntity.getId())
                        .txnType(mapped)
                        .amountTry(amount)
                        .feeTry(BigDecimal.ZERO)
                        .txnDate(date)
                        .notes(NOTE_PREFIX + (note != null && !note.isBlank() ? " " + note : ""))
                        .build();
                txnRepo.save(txn);
                existingFingerprints.add(fingerprint);
                imported++;
            }

            if (commit) {
                log.info("Excel import committed for user {}: imported={} skipped={} warnings={}",
                        userId, imported, skipped, warnings);
            }

            return new ImportSummary(rows.size(), imported, skipped, warnings, rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }
    }

    private Map<String, Asset> indexAssets() {
        Map<String, Asset> map = new HashMap<>();
        for (Asset a : assetRepo.findAll()) {
            map.putIfAbsent(a.getSymbol().toUpperCase(Locale.ROOT), a);
        }
        return map;
    }

    private Portfolio ensurePortfolio(UUID userId, String name, Portfolio.PortfolioType type) {
        return portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> portfolioRepo.save(Portfolio.builder()
                        .userId(userId)
                        .name(name)
                        .portfolioType(type)
                        .description("Imported from Yatirim_Takip_v2.xlsx")
                        .active(true)
                        .build()));
    }

    private Set<String> loadExistingFingerprints(UUID... portfolioIds) {
        Set<String> set = new HashSet<>();
        List<InvestmentTransaction> existing = txnRepo
                .findByPortfolioIdInAndNotesStartingWith(List.of(portfolioIds), NOTE_PREFIX);
        for (InvestmentTransaction t : existing) {
            set.add(fingerprint(t.getPortfolioId(), t.getAssetId(), t.getTxnType(), t.getTxnDate(), t.getAmountTry()));
        }
        return set;
    }

    private String fingerprint(UUID portfolioId, UUID assetId, InvestmentTransaction.TxnType type,
                               LocalDate date, BigDecimal amount) {
        return portfolioId + "|" + assetId + "|" + type + "|" + date + "|" + amount.stripTrailingZeros().toPlainString();
    }

    private InvestmentTransaction.TxnType mapType(String raw) {
        if (raw == null) return null;
        String n = normalize(raw);
        return switch (n) {
            case "yatirim" -> InvestmentTransaction.TxnType.BUY;
            case "para cekme" -> InvestmentTransaction.TxnType.WITHDRAW;
            case "fon degisikligi" -> InvestmentTransaction.TxnType.REBALANCE;
            case "kar alma" -> InvestmentTransaction.TxnType.SELL;
            case "bes katki" -> InvestmentTransaction.TxnType.BES_CONTRIBUTION;
            default -> null;
        };
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ü', 'u')
                .replace('Ü', 'u')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .trim();
    }

    private LocalDate readDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
            }
            String text = readString(cell);
            if (text != null && !text.isBlank()) {
                return LocalDate.parse(text);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String readString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.toString();
            default -> null;
        };
    }

    private BigDecimal readNumber(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String s = cell.getStringCellValue().replace(',', '.').trim();
                    if (s.isEmpty()) yield null;
                    yield new BigDecimal(s);
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
