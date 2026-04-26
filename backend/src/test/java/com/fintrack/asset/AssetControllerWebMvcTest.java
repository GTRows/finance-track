package com.fintrack.asset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.price.PriceHistoryRepository;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AssetControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AssetRepository assetRepository;
    @MockBean PriceHistoryRepository priceHistoryRepository;
    @MockBean TefasFundService tefasFundService;
    @MockBean TefasClient tefasClient;
    @MockBean CoinGeckoClient coinGeckoClient;
    @MockBean YahooFinanceClient yahooFinanceClient;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private Asset sample(UUID id) {
        return Asset.builder()
                .id(id)
                .symbol("BTC")
                .name("Bitcoin")
                .assetType(Asset.AssetType.CRYPTO)
                .currency("USD")
                .price(new BigDecimal("60000"))
                .priceUsd(new BigDecimal("60000"))
                .priceUpdatedAt(Instant.parse("2026-04-01T10:00:00Z"))
                .build();
    }

    @Test
    void listReturnsAssets() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(assetRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].symbol").value("BTC"));
    }

    @Test
    void listFilteredByTypeUsesRepoFilter() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO))
                .thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/assets").param("type", "CRYPTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetType").value("CRYPTO"));
    }

    @Test
    void getReturnsAsset() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(eq(id))).thenReturn(Optional.of(sample(id)));

        mockMvc.perform(get("/api/v1/assets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(eq(id))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/assets/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void historyReturns404WhenAssetMissing() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(eq(id))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/assets/" + id + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void searchTefasReturnsRows() throws Exception {
        stubAuthenticatedUser();
        when(tefasFundService.search("AKB"))
                .thenReturn(
                        List.of(
                                new TefasFundService.FundSearchRow(
                                        "AKB", "Akbank Fund", TefasClient.FundType.YAT, false)));

        mockMvc.perform(get("/api/v1/assets/tefas/search").param("q", "AKB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("AKB"))
                .andExpect(jsonPath("$[0].imported").value(false));
    }
}
