package com.fintrack.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.auth.UserRepository;
import com.fintrack.bills.BillPaymentRepository;
import com.fintrack.bills.BillRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.User;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins the query-count contract for the dashboard composite + bill listing read paths refactored in
 * 30-01. A regression here surfaces re-introduction of N+1 by raising {@link
 * Statistics#getQueryExecutionCount()} above the constant cap. Each scenario exercises the BATCHED
 * REPOSITORY method directly because the {@code @DataJpaTest} slice does not auto-load
 * service-layer beans; the service-layer "did the loop go away?" assertion lives as Mockito
 * verification on each refactored {@code *ServiceTest}.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class QueryCountRegressionTest extends AbstractDataJpaTestSupport {

    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired EntityManager em;
    @Autowired HoldingRepository holdingRepo;
    @Autowired BillPaymentRepository billPaymentRepo;
    @Autowired AssetRepository assetRepo;
    @Autowired PortfolioRepository portfolioRepo;
    @Autowired BillRepository billRepo;
    @Autowired UserRepository userRepo;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

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
                                .price(new BigDecimal("100"))
                                .build())
                .getId();
    }

    private void seedHolding(UUID portfolioId, UUID assetId, String quantity) {
        holdingRepo.save(
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal(quantity))
                        .build());
    }

    private Bill seedBill(UUID userId, String name) {
        return billRepo.save(
                Bill.builder()
                        .userId(userId)
                        .name(name)
                        .amount(new BigDecimal("100"))
                        .dueDay(15)
                        .build());
    }

    private BillPayment seedPayment(UUID billId, String period, BillPayment.PaymentStatus status) {
        return billPaymentRepo.save(
                BillPayment.builder()
                        .billId(billId)
                        .period(period)
                        .amount(new BigDecimal("100"))
                        .status(status)
                        .build());
    }

    @Test
    void holdingsByPortfolioIdIn_executesAtMostOneQuery() {
        UUID userId = seedUser("qc-holdings");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID p3 = seedPortfolio(userId, "P3");
        UUID btc = seedAsset("QC_BTC");
        UUID eth = seedAsset("QC_ETH");
        for (UUID p : List.of(p1, p2, p3)) {
            seedHolding(p, btc, "1");
            seedHolding(p, eth, "2");
        }
        em.flush();
        em.clear();
        statistics.clear();

        List<PortfolioHolding> rows = holdingRepo.findByPortfolioIdIn(List.of(p1, p2, p3));

        assertThat(rows).hasSize(6);
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void billPaymentsByBillIdInAndPeriod_executesAtMostOneQuery() {
        UUID userId = seedUser("qc-bp-period");
        Bill b1 = seedBill(userId, "B1");
        Bill b2 = seedBill(userId, "B2");
        Bill b3 = seedBill(userId, "B3");
        for (Bill b : List.of(b1, b2, b3)) {
            seedPayment(b.getId(), "2026-04", BillPayment.PaymentStatus.PAID);
        }
        em.flush();
        em.clear();
        statistics.clear();

        List<BillPayment> rows =
                billPaymentRepo.findByBillIdInAndPeriod(
                        List.of(b1.getId(), b2.getId(), b3.getId()), "2026-04");

        assertThat(rows).hasSize(3);
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void billPaymentsByBillIdInAndStatusOrderByPeriodDesc_executesAtMostOneQuery() {
        UUID userId = seedUser("qc-bp-status");
        Bill b1 = seedBill(userId, "B1");
        Bill b2 = seedBill(userId, "B2");
        Bill b3 = seedBill(userId, "B3");
        for (Bill b : List.of(b1, b2, b3)) {
            seedPayment(b.getId(), "2026-04", BillPayment.PaymentStatus.PAID);
        }
        em.flush();
        em.clear();
        statistics.clear();

        List<BillPayment> rows =
                billPaymentRepo.findByBillIdInAndStatusOrderByPeriodDesc(
                        List.of(b1.getId(), b2.getId(), b3.getId()),
                        BillPayment.PaymentStatus.PAID);

        assertThat(rows).hasSize(3);
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void assetsByIdsBatched_executesAtMostOneQuery() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(seedAsset("QC_ASSET_" + i));
        }
        em.flush();
        em.clear();
        statistics.clear();

        List<Asset> rows = (List<Asset>) assetRepo.findAllById(ids);

        assertThat(rows).hasSize(5);
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void dashboardComposite_executesAtMostFiveQueries() {
        UUID userId = seedUser("qc-dash");
        UUID p1 = seedPortfolio(userId, "P1");
        UUID p2 = seedPortfolio(userId, "P2");
        UUID p3 = seedPortfolio(userId, "P3");
        List<UUID> assetIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            assetIds.add(seedAsset("QC_DASH_" + i));
        }
        // Two holdings per portfolio = 6 holdings spanning 5 distinct assets.
        seedHolding(p1, assetIds.get(0), "1");
        seedHolding(p1, assetIds.get(1), "2");
        seedHolding(p2, assetIds.get(2), "3");
        seedHolding(p2, assetIds.get(3), "4");
        seedHolding(p3, assetIds.get(4), "5");
        seedHolding(p3, assetIds.get(0), "6");

        Bill b1 = seedBill(userId, "DashB1");
        Bill b2 = seedBill(userId, "DashB2");
        Bill b3 = seedBill(userId, "DashB3");
        Bill b4 = seedBill(userId, "DashB4");
        for (Bill b : List.of(b1, b2, b3, b4)) {
            seedPayment(b.getId(), "2026-04", BillPayment.PaymentStatus.PAID);
        }
        em.flush();
        em.clear();
        statistics.clear();

        // Mirror DashboardService.build read path: portfolios + holdings + assets + bills +
        // payments.
        List<Portfolio> portfolios =
                portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::getId).toList();
        List<PortfolioHolding> holdings = holdingRepo.findByPortfolioIdIn(portfolioIds);
        assetRepo.findAllById(holdings.stream().map(PortfolioHolding::getAssetId).toList());
        List<Bill> bills = billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId);
        billPaymentRepo.findByBillIdInAndPeriod(
                bills.stream().map(Bill::getId).toList(), "2026-04");

        assertThat(portfolios).hasSize(3);
        assertThat(holdings).hasSize(6);
        assertThat(bills).hasSize(4);
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(5);
    }
}
