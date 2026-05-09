package com.fintrack.portfolio.holding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class HoldingRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired HoldingRepository repo;
    @Autowired AssetRepository assetRepo;
    @Autowired PortfolioRepository portfolioRepo;
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

    private UUID seedPortfolio(UUID userId, String name) {
        return portfolioRepo
                .save(Portfolio.builder().userId(userId).name(name).active(true).build())
                .getId();
    }

    private UUID seedAsset(String symbol) {
        return assetRepo
                .save(
                        Asset.builder()
                                .symbol(symbol)
                                .name(symbol)
                                .assetType(Asset.AssetType.CRYPTO)
                                .currency("TRY")
                                .build())
                .getId();
    }

    private PortfolioHolding holding(UUID portfolioId, UUID assetId, String quantity) {
        return PortfolioHolding.builder()
                .portfolioId(portfolioId)
                .assetId(assetId)
                .quantity(new BigDecimal(quantity))
                .build();
    }

    @Test
    void findByPortfolioIdScopesToPortfolio() {
        UUID userId = seedUser("ali");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID asset = seedAsset("BTC");
        repo.save(holding(p1, asset, "1"));
        repo.save(holding(p2, asset, "2"));

        assertThat(repo.findByPortfolioId(p1))
                .extracting(PortfolioHolding::getPortfolioId)
                .containsOnly(p1);
    }

    @Test
    void findByPortfolioIdAndAssetIdReturnsExactMatch() {
        UUID userId = seedUser("ada");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("ETH");
        repo.save(holding(portfolio, asset, "5"));

        assertThat(repo.findByPortfolioIdAndAssetId(portfolio, asset)).isPresent();
        assertThat(repo.findByPortfolioIdAndAssetId(portfolio, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdAndPortfolioIdEnforcesScope() {
        UUID userId = seedUser("kemal");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID asset = seedAsset("XAU");
        PortfolioHolding own = repo.save(holding(p1, asset, "10"));

        assertThat(repo.findByIdAndPortfolioId(own.getId(), p1)).isPresent();
        assertThat(repo.findByIdAndPortfolioId(own.getId(), p2)).isEmpty();
    }

    @Test
    void findByPortfolioIdInReturnsHoldingsAcrossRequestedPortfoliosOnly() {
        UUID userId = seedUser("zeynep");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID p3 = seedPortfolio(userId, "P3");
        UUID btc = seedAsset("BTC2");
        UUID eth = seedAsset("ETH2");
        repo.save(holding(p1, btc, "1"));
        repo.save(holding(p1, eth, "2"));
        repo.save(holding(p2, btc, "3"));
        repo.save(holding(p2, eth, "4"));
        repo.save(holding(p3, btc, "5"));
        repo.save(holding(p3, eth, "6"));

        List<PortfolioHolding> rows = repo.findByPortfolioIdIn(List.of(p1, p2));

        assertThat(rows).hasSize(4);
        assertThat(rows)
                .extracting(PortfolioHolding::getPortfolioId)
                .allSatisfy(id -> assertThat(id).isIn(p1, p2));
    }
}
