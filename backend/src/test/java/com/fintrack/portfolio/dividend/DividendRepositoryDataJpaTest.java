package com.fintrack.portfolio.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Dividend;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class DividendRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired DividendRepository repo;
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

    private Dividend dividend(UUID portfolioId, UUID assetId, LocalDate date, String netTry) {
        return Dividend.builder()
                .portfolioId(portfolioId)
                .assetId(assetId)
                .grossAmount(new BigDecimal(netTry))
                .netAmount(new BigDecimal(netTry))
                .netAmountTry(new BigDecimal(netTry))
                .currency("TRY")
                .paymentDate(date)
                .build();
    }

    @Test
    void findByPortfolioIdOrdersPaymentDateDesc() {
        UUID userId = seedUser("ali");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("AAPL");
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 1, 15), "100"));
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 4, 15), "120"));
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 2, 15), "110"));

        assertThat(repo.findByPortfolioIdOrderByPaymentDateDesc(portfolio))
                .extracting(Dividend::getPaymentDate)
                .containsExactly(
                        LocalDate.of(2026, 4, 15),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 1, 15));
    }

    @Test
    void findByAssetIdScopesToAsset() {
        UUID userId = seedUser("ada");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID a = seedAsset("MSFT");
        UUID b = seedAsset("GOOG");
        repo.save(dividend(portfolio, a, LocalDate.of(2026, 1, 1), "10"));
        repo.save(dividend(portfolio, b, LocalDate.of(2026, 1, 1), "20"));

        assertThat(repo.findByAssetIdOrderByPaymentDateDesc(a))
                .extracting(Dividend::getAssetId)
                .containsOnly(a);
    }

    @Test
    void findByIdAndPortfolioIdEnforcesOwnership() {
        UUID userId = seedUser("kemal");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID asset = seedAsset("KO");
        Dividend own = repo.save(dividend(p1, asset, LocalDate.of(2026, 5, 1), "30"));

        assertThat(repo.findByIdAndPortfolioId(own.getId(), p1)).isPresent();
        assertThat(repo.findByIdAndPortfolioId(own.getId(), p2)).isEmpty();
    }

    @Test
    void sumNetByPortfolioAndAssetAggregates() {
        UUID userId = seedUser("ozge");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("PEP");
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 1, 1), "100"));
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 4, 1), "150"));

        assertThat(repo.sumNetByPortfolioAndAsset(portfolio, asset)).isEqualByComparingTo("250");
    }

    @Test
    void sumNetByPortfoliosAndRangeFiltersDates() {
        UUID userId = seedUser("baris");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("KO2");
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 1, 15), "100"));
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 6, 15), "200"));

        assertThat(
                        repo.sumNetByPortfoliosAndRange(
                                List.of(portfolio),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 3, 31)))
                .isEqualByComparingTo("100");
    }

    @Test
    void sumOutsideRangeReturnsZero() {
        UUID userId = seedUser("yusuf");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("GE");
        repo.save(dividend(portfolio, asset, LocalDate.of(2026, 1, 1), "100"));

        assertThat(
                        repo.sumNetByPortfoliosAndRange(
                                List.of(portfolio),
                                LocalDate.of(2027, 1, 1),
                                LocalDate.of(2027, 12, 31)))
                .isEqualByComparingTo("0");
    }

    @Test
    void sumStoppageTotalsReturnsThreeBigDecimals() {
        UUID userId = seedUser("ela");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID asset = seedAsset("ASELS");
        repo.save(stoppage(portfolio, asset, LocalDate.of(2026, 3, 1), "1000", "150", "850"));
        repo.save(stoppage(portfolio, asset, LocalDate.of(2026, 6, 1), "2000", "300", "1700"));

        Object[] totals =
                repo.sumStoppageTotalsByPortfoliosAndRange(
                        List.of(portfolio), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(totals).hasSize(3);
        assertThat((BigDecimal) totals[0]).isEqualByComparingTo("3000");
        assertThat((BigDecimal) totals[1]).isEqualByComparingTo("450");
        assertThat((BigDecimal) totals[2]).isEqualByComparingTo("2550");
    }

    @Test
    void sumStoppageByAssetGroupsByAssetId() {
        UUID userId = seedUser("ozan");
        UUID portfolio = seedPortfolio(userId, "Main");
        UUID a = seedAsset("THYAO");
        UUID b = seedAsset("AKBNK");
        repo.save(stoppage(portfolio, a, LocalDate.of(2026, 4, 1), "1000", "150", "850"));
        repo.save(stoppage(portfolio, a, LocalDate.of(2026, 7, 1), "500", "75", "425"));
        repo.save(stoppage(portfolio, b, LocalDate.of(2026, 5, 1), "2000", "300", "1700"));

        List<Object[]> rows =
                repo.sumStoppageByAssetAndPortfoliosAndRange(
                        List.of(portfolio), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(rows).hasSize(2);
        Map<UUID, Object[]> byAsset =
                rows.stream().collect(Collectors.toMap(r -> (UUID) r[0], r -> r));
        assertThat((BigDecimal) byAsset.get(a)[1]).isEqualByComparingTo("1500");
        assertThat((BigDecimal) byAsset.get(a)[2]).isEqualByComparingTo("225");
        assertThat((BigDecimal) byAsset.get(b)[1]).isEqualByComparingTo("2000");
        assertThat((BigDecimal) byAsset.get(b)[2]).isEqualByComparingTo("300");
    }

    private Dividend stoppage(
            UUID portfolioId,
            UUID assetId,
            LocalDate date,
            String gross,
            String withholding,
            String netTry) {
        return Dividend.builder()
                .portfolioId(portfolioId)
                .assetId(assetId)
                .grossAmount(new BigDecimal(gross))
                .withholdingTax(new BigDecimal(withholding))
                .netAmount(new BigDecimal(netTry))
                .netAmountTry(new BigDecimal(netTry))
                .currency("TRY")
                .paymentDate(date)
                .build();
    }
}
