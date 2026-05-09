package com.fintrack.portfolio.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class SnapshotRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired SnapshotRepository repo;
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

    private PortfolioSnapshot snapshot(UUID portfolioId, LocalDate date, String value) {
        return PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .snapshotDate(date)
                .totalValueTry(new BigDecimal(value))
                .build();
    }

    @Test
    void findByPortfolioIdOrdersAscending() {
        UUID userId = seedUser("ali");
        UUID portfolio = seedPortfolio(userId, "Main");
        repo.save(snapshot(portfolio, LocalDate.of(2026, 3, 1), "300"));
        repo.save(snapshot(portfolio, LocalDate.of(2026, 1, 1), "100"));
        repo.save(snapshot(portfolio, LocalDate.of(2026, 2, 1), "200"));

        assertThat(repo.findByPortfolioIdOrderBySnapshotDateAsc(portfolio))
                .extracting(PortfolioSnapshot::getSnapshotDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1));
    }

    @Test
    void findByPortfolioIdAndSnapshotDateReturnsExactMatch() {
        UUID userId = seedUser("ada");
        UUID portfolio = seedPortfolio(userId, "Main");
        repo.save(snapshot(portfolio, LocalDate.of(2026, 4, 1), "500"));

        assertThat(repo.findByPortfolioIdAndSnapshotDate(portfolio, LocalDate.of(2026, 4, 1)))
                .isPresent();
        assertThat(repo.findByPortfolioIdAndSnapshotDate(portfolio, LocalDate.of(2026, 4, 2)))
                .isEmpty();
    }

    @Test
    void sumLatestTotalValueTryAggregatesAcrossPortfolios() {
        UUID userId = seedUser("kemal");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        repo.save(snapshot(p1, LocalDate.of(2026, 1, 1), "100"));
        repo.save(snapshot(p1, LocalDate.of(2026, 4, 1), "300"));
        repo.save(snapshot(p2, LocalDate.of(2026, 4, 1), "400"));

        assertThat(repo.sumLatestTotalValueTry()).isEqualByComparingTo("700");
    }

    @Test
    void sumLatestTotalValueTryReturnsZeroWhenEmpty() {
        assertThat(repo.sumLatestTotalValueTry()).isEqualByComparingTo("0");
    }

    @Test
    void findByPortfolioIdAndSnapshotDateBetweenReturnsChronologicalSubset() {
        UUID userId = seedUser("merve");
        UUID portfolio = seedPortfolio(userId, "Main");
        repo.save(snapshot(portfolio, LocalDate.of(2026, 1, 1), "100"));
        repo.save(snapshot(portfolio, LocalDate.of(2026, 2, 1), "200"));
        repo.save(snapshot(portfolio, LocalDate.of(2026, 3, 1), "300"));
        repo.save(snapshot(portfolio, LocalDate.of(2026, 4, 1), "400"));

        assertThat(
                        repo.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                                portfolio, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)))
                .extracting(PortfolioSnapshot::getSnapshotDate)
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void findByPortfolioIdAndSnapshotDateBetweenEmptyRangeReturnsEmpty() {
        UUID userId = seedUser("emir");
        UUID portfolio = seedPortfolio(userId, "Main");
        repo.save(snapshot(portfolio, LocalDate.of(2026, 1, 1), "100"));

        assertThat(
                        repo.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                                portfolio, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1)))
                .isEmpty();
    }
}
