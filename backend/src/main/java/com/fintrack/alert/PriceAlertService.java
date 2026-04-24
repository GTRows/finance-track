package com.fintrack.alert;

import com.fintrack.alert.dto.AlertResponse;
import com.fintrack.alert.dto.CreateAlertRequest;
import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceAlert;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.metrics.BusinessMetrics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceAlertService {

    private final PriceAlertRepository alertRepo;
    private final AlertNotificationRepository notificationRepo;
    private final AssetRepository assetRepo;
    private final BusinessMetrics businessMetrics;

    @Transactional(readOnly = true)
    public List<AlertResponse> listForUser(UUID userId) {
        return alertRepo.findAllByUserId(userId).stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse create(UUID userId, CreateAlertRequest req) {
        Asset asset =
                assetRepo
                        .findById(req.assetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        PriceAlert alert =
                PriceAlert.builder()
                        .userId(userId)
                        .asset(asset)
                        .direction(req.direction())
                        .thresholdTry(req.thresholdTry())
                        .status(PriceAlert.Status.ACTIVE)
                        .build();
        alert = alertRepo.save(alert);
        log.info(
                "Alert created: id={} userId={} asset={} direction={} threshold={}",
                alert.getId(),
                userId,
                asset.getSymbol(),
                alert.getDirection(),
                alert.getThresholdTry());
        return AlertResponse.from(alert);
    }

    @Transactional
    public void delete(UUID userId, UUID alertId) {
        PriceAlert alert =
                alertRepo
                        .findByIdAndUserId(alertId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
        alertRepo.delete(alert);
        log.info("Alert deleted: id={}", alertId);
    }

    @Transactional
    public AlertResponse disable(UUID userId, UUID alertId) {
        PriceAlert alert =
                alertRepo
                        .findByIdAndUserId(alertId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
        alert.setStatus(PriceAlert.Status.DISABLED);
        return AlertResponse.from(alert);
    }

    /**
     * Evaluate every ACTIVE alert against its asset's current TRY price. Flip crossed alerts to
     * TRIGGERED and emit a notification row.
     */
    @Transactional
    public int evaluateAll() {
        List<PriceAlert> active = alertRepo.findAllActiveWithAsset();
        int triggered = 0;
        Instant now = Instant.now();

        for (PriceAlert alert : active) {
            BigDecimal price = alert.getAsset().getPrice();
            if (price == null) continue;

            boolean crossed =
                    switch (alert.getDirection()) {
                        case ABOVE -> price.compareTo(alert.getThresholdTry()) >= 0;
                        case BELOW -> price.compareTo(alert.getThresholdTry()) <= 0;
                    };
            if (!crossed) continue;

            alert.setStatus(PriceAlert.Status.TRIGGERED);
            alert.setTriggeredAt(now);

            AlertNotification notification =
                    AlertNotification.builder()
                            .alertId(alert.getId())
                            .userId(alert.getUserId())
                            .assetId(alert.getAsset().getId())
                            .message(buildMessage(alert, price))
                            .build();
            notificationRepo.save(notification);
            businessMetrics.recordAlertFired("price");
            triggered++;
        }

        if (triggered > 0) {
            log.info("Price alert evaluation triggered {} alert(s)", triggered);
        }
        return triggered;
    }

    private String buildMessage(PriceAlert alert, BigDecimal price) {
        String arrow = alert.getDirection() == PriceAlert.Direction.ABOVE ? ">=" : "<=";
        return alert.getAsset().getSymbol()
                + " "
                + arrow
                + " "
                + alert.getThresholdTry().toPlainString()
                + " TRY (current: "
                + price.toPlainString()
                + ")";
    }
}
