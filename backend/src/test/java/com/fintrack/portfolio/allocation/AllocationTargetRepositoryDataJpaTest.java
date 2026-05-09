package com.fintrack.portfolio.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioAllocationTarget;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AllocationTargetRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AllocationTargetRepository repo;
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

    private PortfolioAllocationTarget target(UUID portfolioId, Asset.AssetType type, String pct) {
        return PortfolioAllocationTarget.builder()
                .portfolioId(portfolioId)
                .assetType(type)
                .targetPercent(new BigDecimal(pct))
                .build();
    }

    @Test
    void findByPortfolioIdReturnsAll() {
        UUID userId = seedUser("ali");
        UUID portfolioId = seedPortfolio(userId, "Main");
        repo.save(target(portfolioId, Asset.AssetType.CRYPTO, "30.00"));
        repo.save(target(portfolioId, Asset.AssetType.STOCK, "70.00"));

        assertThat(repo.findByPortfolioId(portfolioId)).hasSize(2);
    }

    @Test
    void findByPortfolioIdEmptyForUnknown() {
        assertThat(repo.findByPortfolioId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteByPortfolioIdRemovesAll() {
        UUID userId = seedUser("ada");
        UUID portfolioId = seedPortfolio(userId, "Main");
        repo.save(target(portfolioId, Asset.AssetType.CRYPTO, "100.00"));

        repo.deleteByPortfolioId(portfolioId);

        assertThat(repo.findByPortfolioId(portfolioId)).isEmpty();
    }
}
