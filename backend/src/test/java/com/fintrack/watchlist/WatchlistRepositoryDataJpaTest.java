package com.fintrack.watchlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.User;
import com.fintrack.common.entity.WatchlistEntry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class WatchlistRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired WatchlistRepository repo;
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

    private UUID seedAsset(String symbol) {
        return assetRepo.save(
                        Asset.builder()
                                .symbol(symbol)
                                .name(symbol)
                                .assetType(Asset.AssetType.CRYPTO)
                                .currency("TRY")
                                .build())
                .getId();
    }

    private WatchlistEntry entry(UUID userId, UUID assetId) {
        return WatchlistEntry.builder().userId(userId).assetId(assetId).build();
    }

    @Test
    void findByUserIdReturnsScoped() {
        UUID a = seedUser("ali");
        UUID b = seedUser("ada");
        UUID asset = seedAsset("BTC");
        repo.save(entry(a, asset));
        repo.save(entry(b, asset));

        assertThat(repo.findByUserIdOrderByCreatedAtDesc(a))
                .extracting(WatchlistEntry::getUserId)
                .containsOnly(a);
    }

    @Test
    void findByUserIdAndAssetIdReturnsExactMatch() {
        UUID userId = seedUser("kemal");
        UUID asset = seedAsset("ETH");
        repo.save(entry(userId, asset));

        assertThat(repo.findByUserIdAndAssetId(userId, asset)).isPresent();
        assertThat(repo.findByUserIdAndAssetId(userId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void existsByUserIdAndAssetIdReflectsInsertion() {
        UUID userId = seedUser("ozge");
        UUID asset = seedAsset("XRP");

        assertThat(repo.existsByUserIdAndAssetId(userId, asset)).isFalse();

        repo.save(entry(userId, asset));

        assertThat(repo.existsByUserIdAndAssetId(userId, asset)).isTrue();
    }

    @Test
    void deleteByUserIdAndAssetIdRemovesPair() {
        UUID userId = seedUser("yusuf");
        UUID asset = seedAsset("SOL");
        repo.save(entry(userId, asset));

        repo.deleteByUserIdAndAssetId(userId, asset);

        assertThat(repo.findByUserIdAndAssetId(userId, asset)).isEmpty();
    }
}
