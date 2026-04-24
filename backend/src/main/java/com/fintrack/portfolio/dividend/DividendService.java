package com.fintrack.portfolio.dividend;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Dividend;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.dividend.dto.DividendResponse;
import com.fintrack.portfolio.dividend.dto.RecordDividendRequest;
import com.fintrack.price.FxConversionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DividendService {

    private static final String PIVOT = "TRY";

    private final DividendRepository dividendRepo;
    private final PortfolioRepository portfolioRepo;
    private final AssetRepository assetRepo;
    private final FxConversionService fxConversionService;

    @Transactional(readOnly = true)
    public List<DividendResponse> listForPortfolio(UUID userId, UUID portfolioId) {
        requireOwnedPortfolio(userId, portfolioId);
        List<Dividend> rows = dividendRepo.findByPortfolioIdOrderByPaymentDateDesc(portfolioId);
        return rows.stream()
                .map(d -> DividendResponse.from(d, assetRepo.findById(d.getAssetId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DividendResponse> listForAsset(UUID userId, UUID assetId) {
        List<UUID> userPortfolioIds =
                portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                        .map(Portfolio::getId)
                        .toList();
        if (userPortfolioIds.isEmpty()) return List.of();
        Asset asset = assetRepo.findById(assetId).orElse(null);
        return dividendRepo.findByAssetIdOrderByPaymentDateDesc(assetId).stream()
                .filter(d -> userPortfolioIds.contains(d.getPortfolioId()))
                .map(d -> DividendResponse.from(d, asset))
                .toList();
    }

    @Transactional
    public DividendResponse record(UUID userId, UUID portfolioId, RecordDividendRequest request) {
        requireOwnedPortfolio(userId, portfolioId);
        Asset asset =
                assetRepo
                        .findById(request.assetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        BigDecimal gross = request.grossAmount();
        BigDecimal withholding =
                request.withholdingTax() != null ? request.withholdingTax() : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(withholding);

        String currency = normalizeCurrency(request.currency());
        BigDecimal netTry =
                currency.equals(PIVOT) ? net : fxConversionService.convert(net, currency, PIVOT);

        Dividend dividend =
                Dividend.builder()
                        .portfolioId(portfolioId)
                        .assetId(request.assetId())
                        .amountPerShare(request.amountPerShare())
                        .shares(request.shares())
                        .grossAmount(gross)
                        .withholdingTax(withholding)
                        .netAmount(net)
                        .currency(currency)
                        .netAmountTry(netTry)
                        .paymentDate(request.paymentDate())
                        .exDividendDate(request.exDividendDate())
                        .notes(request.notes())
                        .build();
        dividend = dividendRepo.save(dividend);
        log.info(
                "Dividend recorded: id={} portfolioId={} assetId={} net={} currency={}",
                dividend.getId(),
                portfolioId,
                request.assetId(),
                net,
                currency);
        return DividendResponse.from(dividend, asset);
    }

    @Transactional
    public void delete(UUID userId, UUID portfolioId, UUID dividendId) {
        requireOwnedPortfolio(userId, portfolioId);
        Dividend dividend =
                dividendRepo
                        .findByIdAndPortfolioId(dividendId, portfolioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Dividend not found"));
        dividendRepo.delete(dividend);
        log.info("Dividend deleted: id={} portfolioId={}", dividendId, portfolioId);
    }

    private Portfolio requireOwnedPortfolio(UUID userId, UUID portfolioId) {
        return portfolioRepo
                .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) return PIVOT;
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
