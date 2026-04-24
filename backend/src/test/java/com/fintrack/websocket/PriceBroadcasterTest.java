package com.fintrack.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PriceBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AssetRepository assetRepository;

    @InjectMocks PriceBroadcaster broadcaster;

    private Asset asset(String symbol, AssetType type, String price, String priceUsd) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(type)
                .currency("TRY")
                .price(price == null ? null : new BigDecimal(price))
                .priceUsd(priceUsd == null ? null : new BigDecimal(priceUsd))
                .priceUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();
    }

    @Test
    void doesNothingWhenNoPricedAssets() {
        when(assetRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of());

        broadcaster.broadcastAll();

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/prices"), any(Object.class));
    }

    @Test
    void skipsAssetsWithNullPrice() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("NP", AssetType.CRYPTO, null, null)));

        broadcaster.broadcastAll();

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/prices"), any(Object.class));
    }

    @Test
    void broadcastsBatchOfPricedAssetsToPricesTopic() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(
                        List.of(
                                asset("BTC", AssetType.CRYPTO, "100", "3"),
                                asset("NP", AssetType.CRYPTO, null, null),
                                asset("ETH", AssetType.CRYPTO, "50", null)));

        broadcaster.broadcastAll();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/prices"), captor.capture());
        PriceBroadcaster.PriceBatch batch = (PriceBroadcaster.PriceBatch) captor.getValue();
        assertThat(batch.count()).isEqualTo(2);
        assertThat(batch.prices())
                .extracting(PriceBroadcaster.PriceUpdate::symbol)
                .containsExactly("BTC", "ETH");
        assertThat(batch.prices().get(0).price()).isEqualByComparingTo("100");
        assertThat(batch.prices().get(0).assetType()).isEqualTo("CRYPTO");
    }

    @Test
    void swallowsMessagingTemplateFailure() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("BTC", AssetType.CRYPTO, "100", "3")));
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate)
                .convertAndSend(eq("/topic/prices"), any(Object.class));

        broadcaster.broadcastAll();

        verify(messagingTemplate).convertAndSend(eq("/topic/prices"), any(Object.class));
    }
}
