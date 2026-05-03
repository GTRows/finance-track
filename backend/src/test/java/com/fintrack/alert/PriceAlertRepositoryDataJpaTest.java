package com.fintrack.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceAlert;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class PriceAlertRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired PriceAlertRepository repo;
    @Autowired AssetRepository assetRepo;
    @Autowired UserRepository userRepo;

    private UUID seedUser(String username) {
        return userRepo.save(
                        User.builder()
                                .username(username)
                                .email(username + "@example.com")
                                .password("bcrypt-hash")
                                .role(User.Role.USER)
                                .build())
                .getId();
    }

    private Asset seedAsset(String symbol) {
        return assetRepo.save(
                Asset.builder()
                        .symbol(symbol)
                        .name(symbol + " Token")
                        .assetType(Asset.AssetType.CRYPTO)
                        .currency("TRY")
                        .build());
    }

    private PriceAlert alert(
            UUID userId, Asset asset, PriceAlert.Direction dir, PriceAlert.Status status) {
        return PriceAlert.builder()
                .userId(userId)
                .asset(asset)
                .direction(dir)
                .thresholdTry(new BigDecimal("100"))
                .status(status)
                .build();
    }

    @Test
    void findAllByUserIdReturnsOnlyOwn() {
        UUID a = seedUser("ali");
        UUID b = seedUser("ada");
        Asset btc = seedAsset("BTC");
        repo.save(alert(a, btc, PriceAlert.Direction.ABOVE, PriceAlert.Status.ACTIVE));
        repo.save(alert(b, btc, PriceAlert.Direction.BELOW, PriceAlert.Status.ACTIVE));

        var rows = repo.findAllByUserId(a);

        assertThat(rows).extracting(PriceAlert::getUserId).containsOnly(a);
    }

    @Test
    void findAllActiveWithAssetExcludesNonActive() {
        UUID userId = seedUser("kemal");
        Asset eth = seedAsset("ETH");
        repo.save(alert(userId, eth, PriceAlert.Direction.ABOVE, PriceAlert.Status.ACTIVE));
        repo.save(alert(userId, eth, PriceAlert.Direction.ABOVE, PriceAlert.Status.TRIGGERED));
        repo.save(alert(userId, eth, PriceAlert.Direction.ABOVE, PriceAlert.Status.DISABLED));

        var actives = repo.findAllActiveWithAsset();

        assertThat(actives)
                .extracting(PriceAlert::getStatus)
                .containsOnly(PriceAlert.Status.ACTIVE);
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("anna");
        UUID b = seedUser("bob");
        Asset gld = seedAsset("XAU");
        PriceAlert own =
                repo.save(alert(a, gld, PriceAlert.Direction.BELOW, PriceAlert.Status.ACTIVE));

        assertThat(repo.findByIdAndUserId(own.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(own.getId(), b)).isEmpty();
    }
}
