package com.fintrack.price;

import com.fintrack.websocket.PriceBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for manually kicking off a price refresh.
 */
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceSyncService priceSyncService;
    private final PriceBroadcaster priceBroadcaster;

    /** Refreshes prices from all configured external sources right now. */
    @PostMapping("/refresh")
    public ResponseEntity<PriceSyncService.SyncResult> refresh() {
        PriceSyncService.SyncResult result = priceSyncService.refreshAll();
        priceBroadcaster.broadcastAll();
        return ResponseEntity.ok(result);
    }
}
