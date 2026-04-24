package com.fintrack.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.alert.dto.AlertResponse;
import com.fintrack.alert.dto.CreateAlertRequest;
import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.PriceAlert;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.metrics.BusinessMetrics;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    @Mock PriceAlertRepository alertRepo;
    @Mock AlertNotificationRepository notificationRepo;
    @Mock AssetRepository assetRepo;
    @Mock BusinessMetrics businessMetrics;

    @InjectMocks PriceAlertService service;

    private final UUID userId = UUID.randomUUID();

    private Asset asset(String symbol, String price) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(AssetType.CRYPTO)
                .currency("TRY")
                .price(price == null ? null : new BigDecimal(price))
                .build();
    }

    private PriceAlert alert(
            Asset asset,
            PriceAlert.Direction direction,
            String threshold,
            PriceAlert.Status status) {
        return PriceAlert.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .asset(asset)
                .direction(direction)
                .thresholdTry(new BigDecimal(threshold))
                .status(status)
                .build();
    }

    @Test
    void listMapsEntitiesToDtos() {
        Asset btc = asset("BTC", "100");
        when(alertRepo.findAllByUserId(userId))
                .thenReturn(
                        List.of(
                                alert(
                                        btc,
                                        PriceAlert.Direction.ABOVE,
                                        "120",
                                        PriceAlert.Status.ACTIVE)));

        List<AlertResponse> res = service.listForUser(userId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).assetSymbol()).isEqualTo("BTC");
        assertThat(res.get(0).direction()).isEqualTo(PriceAlert.Direction.ABOVE);
        assertThat(res.get(0).thresholdTry()).isEqualByComparingTo("120");
    }

    @Test
    void createRequiresAssetToExist() {
        UUID assetId = UUID.randomUUID();
        when(assetRepo.findById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new CreateAlertRequest(
                                                assetId,
                                                PriceAlert.Direction.ABOVE,
                                                new BigDecimal("100"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(alertRepo, never()).save(any());
    }

    @Test
    void createPersistsActiveAlert() {
        Asset btc = asset("BTC", "100");
        when(assetRepo.findById(btc.getId())).thenReturn(Optional.of(btc));
        when(alertRepo.save(any(PriceAlert.class)))
                .thenAnswer(
                        inv -> {
                            PriceAlert a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });

        AlertResponse res =
                service.create(
                        userId,
                        new CreateAlertRequest(
                                btc.getId(), PriceAlert.Direction.BELOW, new BigDecimal("50")));

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepo).save(captor.capture());
        PriceAlert saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getDirection()).isEqualTo(PriceAlert.Direction.BELOW);
        assertThat(saved.getStatus()).isEqualTo(PriceAlert.Status.ACTIVE);
        assertThat(res.status()).isEqualTo(PriceAlert.Status.ACTIVE);
    }

    @Test
    void deleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(alertRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(alertRepo, never()).delete(any());
    }

    @Test
    void deleteRemovesWhenOwned() {
        PriceAlert a =
                alert(
                        asset("BTC", "100"),
                        PriceAlert.Direction.ABOVE,
                        "120",
                        PriceAlert.Status.ACTIVE);
        when(alertRepo.findByIdAndUserId(a.getId(), userId)).thenReturn(Optional.of(a));

        service.delete(userId, a.getId());

        verify(alertRepo).delete(a);
    }

    @Test
    void disableRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(alertRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disableFlipsStatus() {
        PriceAlert a =
                alert(
                        asset("BTC", "100"),
                        PriceAlert.Direction.ABOVE,
                        "120",
                        PriceAlert.Status.ACTIVE);
        when(alertRepo.findByIdAndUserId(a.getId(), userId)).thenReturn(Optional.of(a));

        AlertResponse res = service.disable(userId, a.getId());

        assertThat(a.getStatus()).isEqualTo(PriceAlert.Status.DISABLED);
        assertThat(res.status()).isEqualTo(PriceAlert.Status.DISABLED);
    }

    @Test
    void evaluateAllTriggersAboveAlertWhenPriceMeetsThreshold() {
        Asset btc = asset("BTC", "120");
        PriceAlert a = alert(btc, PriceAlert.Direction.ABOVE, "120", PriceAlert.Status.ACTIVE);
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of(a));

        int triggered = service.evaluateAll();

        assertThat(triggered).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo(PriceAlert.Status.TRIGGERED);
        assertThat(a.getTriggeredAt()).isNotNull();
        ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
        verify(notificationRepo).save(captor.capture());
        AlertNotification n = captor.getValue();
        assertThat(n.getAssetId()).isEqualTo(btc.getId());
        assertThat(n.getAlertId()).isEqualTo(a.getId());
        assertThat(n.getMessage()).contains("BTC").contains(">=").contains("120");
        verify(businessMetrics).recordAlertFired("price");
    }

    @Test
    void evaluateAllTriggersBelowAlertWhenPriceAtOrUnderThreshold() {
        Asset btc = asset("BTC", "50");
        PriceAlert a = alert(btc, PriceAlert.Direction.BELOW, "50", PriceAlert.Status.ACTIVE);
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of(a));

        int triggered = service.evaluateAll();

        assertThat(triggered).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo(PriceAlert.Status.TRIGGERED);
        verify(notificationRepo).save(any());
    }

    @Test
    void evaluateAllSkipsAlertsWithNullAssetPrice() {
        Asset priceless = asset("NP", null);
        PriceAlert a =
                alert(priceless, PriceAlert.Direction.ABOVE, "100", PriceAlert.Status.ACTIVE);
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of(a));

        int triggered = service.evaluateAll();

        assertThat(triggered).isZero();
        assertThat(a.getStatus()).isEqualTo(PriceAlert.Status.ACTIVE);
        verify(notificationRepo, never()).save(any());
        verify(businessMetrics, never()).recordAlertFired(any());
    }

    @Test
    void evaluateAllLeavesAlertsBelowThresholdUntouched() {
        Asset btc = asset("BTC", "80");
        PriceAlert a = alert(btc, PriceAlert.Direction.ABOVE, "120", PriceAlert.Status.ACTIVE);
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of(a));

        int triggered = service.evaluateAll();

        assertThat(triggered).isZero();
        assertThat(a.getStatus()).isEqualTo(PriceAlert.Status.ACTIVE);
    }

    @Test
    void evaluateAllHandlesMultipleAlertsIndependently() {
        Asset btc = asset("BTC", "120");
        Asset eth = asset("ETH", "30");
        PriceAlert above = alert(btc, PriceAlert.Direction.ABOVE, "100", PriceAlert.Status.ACTIVE);
        PriceAlert below = alert(eth, PriceAlert.Direction.BELOW, "20", PriceAlert.Status.ACTIVE);
        PriceAlert untouched =
                alert(btc, PriceAlert.Direction.BELOW, "50", PriceAlert.Status.ACTIVE);
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of(above, below, untouched));

        int triggered = service.evaluateAll();

        assertThat(triggered).isEqualTo(1);
        assertThat(above.getStatus()).isEqualTo(PriceAlert.Status.TRIGGERED);
        assertThat(below.getStatus()).isEqualTo(PriceAlert.Status.ACTIVE);
        assertThat(untouched.getStatus()).isEqualTo(PriceAlert.Status.ACTIVE);
        verify(businessMetrics, times(1)).recordAlertFired(eq("price"));
    }

    @Test
    void evaluateAllNoActiveAlertsReturnsZero() {
        when(alertRepo.findAllActiveWithAsset()).thenReturn(List.of());

        assertThat(service.evaluateAll()).isZero();
        verify(notificationRepo, never()).save(any());
    }
}
