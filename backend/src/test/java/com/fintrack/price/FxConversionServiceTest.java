package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fintrack.price.client.ExchangeRateClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxConversionServiceTest {

    @Mock ExchangeRateClient exchangeRateClient;

    @InjectMocks FxConversionService service;

    @Test
    void returnsAmountUnchangedWhenCurrenciesMatch() {
        BigDecimal result = service.convert(new BigDecimal("123.4567"), "TRY", "TRY");
        assertThat(result).isEqualByComparingTo("123.46");
    }

    @Test
    void normalizesCurrencyCaseAndWhitespace() {
        BigDecimal result = service.convert(new BigDecimal("50"), " try ", "TRY");
        assertThat(result).isEqualByComparingTo("50.00");
    }

    @Test
    void convertsFromForeignCurrencyToTry() {
        when(exchangeRateClient.fetchTryRates(List.of("USD")))
                .thenReturn(Map.of("USD", new BigDecimal("32.5")));

        BigDecimal result = service.convert(new BigDecimal("100"), "USD", "TRY");

        assertThat(result).isEqualByComparingTo("3250.00");
    }

    @Test
    void convertsFromTryToForeignCurrency() {
        when(exchangeRateClient.fetchTryRates(List.of("USD")))
                .thenReturn(Map.of("USD", new BigDecimal("32.5")));

        BigDecimal result = service.convert(new BigDecimal("3250"), "TRY", "USD");

        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void crossesTwoForeignCurrenciesViaTryPivot() {
        when(exchangeRateClient.fetchTryRates(List.of("USD")))
                .thenReturn(Map.of("USD", new BigDecimal("32")));
        when(exchangeRateClient.fetchTryRates(List.of("EUR")))
                .thenReturn(Map.of("EUR", new BigDecimal("36")));

        BigDecimal result = service.convert(new BigDecimal("90"), "EUR", "USD");

        assertThat(result).isEqualByComparingTo("101.25");
    }

    @Test
    void throwsWhenRateMissing() {
        when(exchangeRateClient.fetchTryRates(anyList())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.convert(BigDecimal.TEN, "USD", "TRY"))
                .isInstanceOf(FxConversionService.CurrencyConversionException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void throwsWhenRateNonPositive() {
        when(exchangeRateClient.fetchTryRates(List.of("USD")))
                .thenReturn(Map.of("USD", BigDecimal.ZERO));

        assertThatThrownBy(() -> service.convert(BigDecimal.TEN, "USD", "TRY"))
                .isInstanceOf(FxConversionService.CurrencyConversionException.class);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> service.convert(null, "USD", "TRY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount");
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, "  ", "TRY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crossRateSameCurrencyReturnsOne() {
        assertThat(service.crossRate("USD", "USD")).isEqualByComparingTo("1");
    }
}
