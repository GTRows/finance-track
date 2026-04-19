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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @Mock DividendRepository dividendRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock AssetRepository assetRepo;
    @Mock FxConversionService fxConversionService;

    @InjectMocks DividendService service;

    private UUID userId;
    private UUID portfolioId;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolioId = UUID.randomUUID();
        assetId = UUID.randomUUID();

        Portfolio portfolio = Portfolio.builder().id(portfolioId).userId(userId).name("Main").build();
        lenient().when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(portfolio));

        Asset asset = Asset.builder().id(assetId).symbol("KO").name("Coca-Cola").build();
        lenient().when(assetRepo.findById(assetId)).thenReturn(Optional.of(asset));
    }

    @Test
    void recordsTryDividendWithoutFxConversion() {
        when(dividendRepo.save(any(Dividend.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordDividendRequest request = new RecordDividendRequest(
                assetId,
                new BigDecimal("100"),
                new BigDecimal("15"),
                "TRY",
                null,
                null,
                LocalDate.of(2026, 4, 1),
                null,
                null
        );

        DividendResponse response = service.record(userId, portfolioId, request);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepo).save(captor.capture());
        Dividend saved = captor.getValue();
        assertThat(saved.getNetAmount()).isEqualByComparingTo("85");
        assertThat(saved.getNetAmountTry()).isEqualByComparingTo("85");
        assertThat(saved.getCurrency()).isEqualTo("TRY");
        assertThat(response.netAmountTry()).isEqualByComparingTo("85");
        verify(fxConversionService, never()).convert(any(), any(), any());
    }

    @Test
    void convertsNonTryDividendToTryAtPaymentDate() {
        when(dividendRepo.save(any(Dividend.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fxConversionService.convert(new BigDecimal("85"), "USD", "TRY"))
                .thenReturn(new BigDecimal("2805.00"));

        RecordDividendRequest request = new RecordDividendRequest(
                assetId,
                new BigDecimal("100"),
                new BigDecimal("15"),
                "usd",
                null,
                null,
                LocalDate.of(2026, 4, 1),
                null,
                null
        );

        service.record(userId, portfolioId, request);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepo).save(captor.capture());
        Dividend saved = captor.getValue();
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getNetAmount()).isEqualByComparingTo("85");
        assertThat(saved.getNetAmountTry()).isEqualByComparingTo("2805");
    }

    @Test
    void defaultsWithholdingTaxToZeroWhenOmitted() {
        when(dividendRepo.save(any(Dividend.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordDividendRequest request = new RecordDividendRequest(
                assetId,
                new BigDecimal("50"),
                null,
                "TRY",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        service.record(userId, portfolioId, request);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepo).save(captor.capture());
        assertThat(captor.getValue().getWithholdingTax()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getNetAmount()).isEqualByComparingTo("50");
    }

    @Test
    void normalizesBlankCurrencyToTry() {
        when(dividendRepo.save(any(Dividend.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordDividendRequest request = new RecordDividendRequest(
                assetId,
                new BigDecimal("30"),
                BigDecimal.ZERO,
                "  ",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        service.record(userId, portfolioId, request);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepo).save(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("TRY");
    }

    @Test
    void rejectsRecordWhenPortfolioNotOwned() {
        UUID otherUser = UUID.randomUUID();
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, otherUser))
                .thenReturn(Optional.empty());

        RecordDividendRequest request = new RecordDividendRequest(
                assetId,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                "TRY",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        assertThatThrownBy(() -> service.record(otherUser, portfolioId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteChecksOwnershipAndRemovesRecord() {
        UUID dividendId = UUID.randomUUID();
        Dividend dividend = Dividend.builder().id(dividendId).portfolioId(portfolioId).build();
        when(dividendRepo.findByIdAndPortfolioId(dividendId, portfolioId))
                .thenReturn(Optional.of(dividend));

        service.delete(userId, portfolioId, dividendId);

        verify(dividendRepo).delete(dividend);
    }
}
