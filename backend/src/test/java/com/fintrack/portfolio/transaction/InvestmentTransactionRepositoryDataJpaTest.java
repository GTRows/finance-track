package com.fintrack.portfolio.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class InvestmentTransactionRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired InvestmentTransactionRepository repo;
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
                                .assetType(Asset.AssetType.STOCK)
                                .currency("TRY")
                                .build())
                .getId();
    }

    private InvestmentTransaction txn(
            UUID portfolioId,
            UUID assetId,
            InvestmentTransaction.TxnType type,
            LocalDate date,
            String notes) {
        return InvestmentTransaction.builder()
                .portfolioId(portfolioId)
                .assetId(assetId)
                .txnType(type)
                .quantity(new BigDecimal("1"))
                .priceTry(new BigDecimal("100"))
                .amountTry(new BigDecimal("100"))
                .txnDate(date)
                .notes(notes)
                .build();
    }

    @Test
    void findByPortfolioIdOrdersByTxnDateDesc() {
        UUID userId = seedUser("ali");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("AAPL");
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 1, 5),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 4, 5),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 2, 5),
                        null));

        assertThat(repo.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(portfolio))
                .extracting(InvestmentTransaction::getTxnDate)
                .containsExactly(
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2026, 2, 5),
                        LocalDate.of(2026, 1, 5));
    }

    @Test
    void findByIdAndPortfolioIdEnforcesScope() {
        UUID userId = seedUser("ada");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID asset = seedAsset("MSFT");
        InvestmentTransaction own =
                repo.save(
                        txn(
                                p1,
                                asset,
                                InvestmentTransaction.TxnType.BUY,
                                LocalDate.of(2026, 4, 5),
                                null));

        assertThat(repo.findByIdAndPortfolioId(own.getId(), p1)).isPresent();
        assertThat(repo.findByIdAndPortfolioId(own.getId(), p2)).isEmpty();
    }

    @Test
    void findByPortfolioIdInAndNotesStartingWithFilters() {
        UUID userId = seedUser("kemal");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("KO");
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 1, 1),
                        "AUTO: rebalance"));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 1, 2),
                        "manual buy"));

        var matched = repo.findByPortfolioIdInAndNotesStartingWith(List.of(portfolio), "AUTO:");

        assertThat(matched).hasSize(1);
    }

    @Test
    void findByPortfolioIdInAndNotesStartingWithEmptyOnNoMatch() {
        UUID userId = seedUser("ozge");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("PEP");
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 1, 1),
                        "manual"));

        assertThat(repo.findByPortfolioIdInAndNotesStartingWith(List.of(portfolio), "AUTO:"))
                .isEmpty();
    }

    @Test
    void findByTxnTypeAndDateLessThanEqualReturnsOnlyMatchingType() {
        UUID userId = seedUser("burak");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("ABC");
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.BUY,
                        LocalDate.of(2026, 1, 1),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.SELL,
                        LocalDate.of(2026, 2, 1),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.SELL,
                        LocalDate.of(2026, 5, 1),
                        null));

        var matches =
                repo.findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                        portfolio, InvestmentTransaction.TxnType.SELL, LocalDate.of(2026, 3, 1));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getTxnDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void findByTxnTypeAndDateLessThanEqualOrdersAscending() {
        UUID userId = seedUser("derya");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("XYZ");
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.SELL,
                        LocalDate.of(2026, 3, 1),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.SELL,
                        LocalDate.of(2026, 1, 1),
                        null));
        repo.save(
                txn(
                        portfolio,
                        asset,
                        InvestmentTransaction.TxnType.SELL,
                        LocalDate.of(2026, 2, 1),
                        null));

        var matches =
                repo.findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                        portfolio, InvestmentTransaction.TxnType.SELL, LocalDate.of(2026, 12, 31));

        assertThat(matches)
                .extracting(InvestmentTransaction::getTxnDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1));
    }
}
