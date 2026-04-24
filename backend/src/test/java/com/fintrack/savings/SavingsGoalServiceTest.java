package com.fintrack.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.entity.SavingsGoal;
import com.fintrack.common.entity.SavingsGoalContribution;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import com.fintrack.savings.dto.ContributionRequest;
import com.fintrack.savings.dto.ContributionResponse;
import com.fintrack.savings.dto.GoalResponse;
import com.fintrack.savings.dto.UpsertGoalRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsGoalServiceTest {

    @Mock SavingsGoalRepository goalRepo;
    @Mock SavingsContributionRepository contributionRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock HoldingRepository holdingRepo;
    @Mock AssetRepository assetRepo;
    @Mock SnapshotRepository snapshotRepo;

    @InjectMocks SavingsGoalService service;

    private final UUID userId = UUID.randomUUID();

    private SavingsGoal goal(String target, UUID linked) {
        return SavingsGoal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("G")
                .targetAmount(new BigDecimal(target))
                .targetDate(LocalDate.of(2026, 12, 31))
                .linkedPortfolioId(linked)
                .build();
    }

    @Test
    void createValidatesLinkedPortfolioAndPersists() {
        Portfolio p =
                Portfolio.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .name("Main")
                        .active(true)
                        .build();
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(p.getId(), userId))
                .thenReturn(Optional.of(p));
        when(goalRepo.save(any(SavingsGoal.class)))
                .thenAnswer(
                        inv -> {
                            SavingsGoal g = inv.getArgument(0);
                            g.setId(UUID.randomUUID());
                            return g;
                        });
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p.getId())).thenReturn(List.of());
        when(holdingRepo.findByPortfolioId(p.getId())).thenReturn(List.of());

        UpsertGoalRequest req =
                new UpsertGoalRequest(
                        "Down payment",
                        new BigDecimal("100000"),
                        LocalDate.of(2027, 6, 1),
                        p.getId(),
                        "note");
        GoalResponse res = service.create(userId, req);

        assertThat(res.name()).isEqualTo("Down payment");
        assertThat(res.linkedPortfolioId()).isEqualTo(p.getId());
        assertThat(res.linkedPortfolioName()).isEqualTo("Main");
        assertThat(res.currentAmount()).isEqualByComparingTo("0");
        assertThat(res.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void createRejectsLinkedPortfolioNotOwned() {
        UUID portfolioId = UUID.randomUUID();
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new UpsertGoalRequest(
                                                "x", BigDecimal.TEN, null, portfolioId, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(goalRepo, never()).save(any());
    }

    @Test
    void createAllowsNullLinkedPortfolio() {
        when(goalRepo.save(any(SavingsGoal.class)))
                .thenAnswer(
                        inv -> {
                            SavingsGoal g = inv.getArgument(0);
                            g.setId(UUID.randomUUID());
                            return g;
                        });
        when(contributionRepo.sumByGoalId(any())).thenReturn(BigDecimal.ZERO);
        when(contributionRepo.findByGoalIdOrderByContributionDateDesc(any())).thenReturn(List.of());

        service.create(userId, new UpsertGoalRequest("x", new BigDecimal("100"), null, null, null));

        verify(portfolioRepo, never()).findByIdAndUserIdAndActiveTrue(any(), any());
    }

    @Test
    void updateRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(goalRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpsertGoalRequest(
                                                "x", BigDecimal.TEN, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMutatesFields() {
        SavingsGoal existing = goal("10000", null);
        when(goalRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(contributionRepo.sumByGoalId(existing.getId())).thenReturn(BigDecimal.ZERO);
        when(contributionRepo.findByGoalIdOrderByContributionDateDesc(existing.getId()))
                .thenReturn(List.of());

        service.update(
                userId,
                existing.getId(),
                new UpsertGoalRequest(
                        "New", new BigDecimal("50000"), LocalDate.of(2028, 1, 1), null, "updated"));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getTargetAmount()).isEqualByComparingTo("50000");
        assertThat(existing.getNotes()).isEqualTo("updated");
    }

    @Test
    void archiveSetsTimestamp() {
        SavingsGoal existing = goal("1000", null);
        when(goalRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.archive(userId, existing.getId());

        assertThat(existing.getArchivedAt()).isNotNull();
    }

    @Test
    void listBuildsResponsesFromContributions() {
        SavingsGoal g = goal("10000", null);
        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(contributionRepo.sumByGoalId(g.getId())).thenReturn(new BigDecimal("2500"));
        when(contributionRepo.findByGoalIdOrderByContributionDateDesc(g.getId()))
                .thenReturn(
                        List.of(
                                SavingsGoalContribution.builder()
                                        .id(UUID.randomUUID())
                                        .goalId(g.getId())
                                        .contributionDate(LocalDate.now().minusDays(10))
                                        .amount(new BigDecimal("900"))
                                        .build(),
                                SavingsGoalContribution.builder()
                                        .id(UUID.randomUUID())
                                        .goalId(g.getId())
                                        .contributionDate(LocalDate.now().minusDays(200))
                                        .amount(new BigDecimal("50000"))
                                        .build()));

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.currentAmount()).isEqualByComparingTo("2500");
        assertThat(res.progressRatio()).isEqualByComparingTo("0.2500");
        assertThat(res.status()).isEqualTo("IN_PROGRESS");
        assertThat(res.monthlyPace()).isEqualByComparingTo("300.00");
        assertThat(res.projectedCompletion()).isNotNull();
    }

    @Test
    void listMarksReachedWhenCurrentMeetsTarget() {
        SavingsGoal g = goal("1000", null);
        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(contributionRepo.sumByGoalId(g.getId())).thenReturn(new BigDecimal("1200"));
        when(contributionRepo.findByGoalIdOrderByContributionDateDesc(g.getId()))
                .thenReturn(List.of());

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.status()).isEqualTo("REACHED");
        assertThat(res.projectedCompletion()).isNull();
    }

    @Test
    void listUsesPortfolioValueWhenGoalLinkedToPortfolio() {
        UUID portfolioId = UUID.randomUUID();
        SavingsGoal g = goal("10000", portfolioId);
        Portfolio p =
                Portfolio.builder()
                        .id(portfolioId)
                        .userId(userId)
                        .name("Main")
                        .active(true)
                        .build();
        Asset btc =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("BTC")
                        .name("BTC")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(new BigDecimal("100"))
                        .build();
        Asset eth =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("ETH")
                        .name("ETH")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(new BigDecimal("50"))
                        .build();
        PortfolioHolding h1 =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("10"))
                        .build();
        PortfolioHolding h2 =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(eth.getId())
                        .quantity(new BigDecimal("20"))
                        .build();

        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of(h1, h2));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc, eth));
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(List.of());
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(p));

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.currentAmount()).isEqualByComparingTo("2000");
        assertThat(res.linkedPortfolioName()).isEqualTo("Main");
    }

    @Test
    void pacePerDayFromSnapshotWindow() {
        UUID portfolioId = UUID.randomUUID();
        SavingsGoal g = goal("10000", portfolioId);
        LocalDate today = LocalDate.now();
        PortfolioSnapshot oldSnap =
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .snapshotDate(today.minusDays(30))
                        .totalValueTry(new BigDecimal("1000"))
                        .build();
        PortfolioSnapshot newSnap =
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .snapshotDate(today)
                        .totalValueTry(new BigDecimal("1300"))
                        .build();

        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(List.of(oldSnap, newSnap));
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(
                        Optional.of(
                                Portfolio.builder()
                                        .id(portfolioId)
                                        .userId(userId)
                                        .name("M")
                                        .active(true)
                                        .build()));

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.monthlyPace()).isEqualByComparingTo("300.00");
    }

    @Test
    void paceIsNullWhenSingleSnapshot() {
        UUID portfolioId = UUID.randomUUID();
        SavingsGoal g = goal("10000", portfolioId);
        PortfolioSnapshot only =
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .snapshotDate(LocalDate.now())
                        .totalValueTry(new BigDecimal("1000"))
                        .build();

        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(holdingRepo.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(List.of(only));
        when(portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(
                        Optional.of(
                                Portfolio.builder()
                                        .id(portfolioId)
                                        .userId(userId)
                                        .name("M")
                                        .active(true)
                                        .build()));

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.monthlyPace()).isNull();
    }

    @Test
    void paceNullWhenNoContributionsInWindow() {
        SavingsGoal g = goal("10000", null);
        when(goalRepo.findActive(userId)).thenReturn(List.of(g));
        when(contributionRepo.sumByGoalId(g.getId())).thenReturn(new BigDecimal("500"));
        when(contributionRepo.findByGoalIdOrderByContributionDateDesc(g.getId()))
                .thenReturn(
                        List.of(
                                SavingsGoalContribution.builder()
                                        .id(UUID.randomUUID())
                                        .goalId(g.getId())
                                        .contributionDate(LocalDate.now().minusDays(300))
                                        .amount(new BigDecimal("500"))
                                        .build()));

        GoalResponse res = service.list(userId).get(0);

        assertThat(res.monthlyPace()).isNull();
        assertThat(res.projectedCompletion()).isNull();
    }

    @Test
    void addContributionRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(goalRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.addContribution(
                                        userId,
                                        id,
                                        new ContributionRequest(
                                                LocalDate.now(), BigDecimal.TEN, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addContributionPersists() {
        SavingsGoal g = goal("1000", null);
        when(goalRepo.findByIdAndUserId(g.getId(), userId)).thenReturn(Optional.of(g));
        when(contributionRepo.save(any(SavingsGoalContribution.class)))
                .thenAnswer(
                        inv -> {
                            SavingsGoalContribution c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        ContributionResponse res =
                service.addContribution(
                        userId,
                        g.getId(),
                        new ContributionRequest(
                                LocalDate.of(2026, 4, 1), new BigDecimal("100"), "apr"));

        ArgumentCaptor<SavingsGoalContribution> captor =
                ArgumentCaptor.forClass(SavingsGoalContribution.class);
        verify(contributionRepo).save(captor.capture());
        assertThat(captor.getValue().getGoalId()).isEqualTo(g.getId());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(res.amount()).isEqualByComparingTo("100");
    }

    @Test
    void deleteContributionRejectsForeignContribution() {
        SavingsGoal g = goal("1000", null);
        when(goalRepo.findByIdAndUserId(g.getId(), userId)).thenReturn(Optional.of(g));
        SavingsGoalContribution foreign =
                SavingsGoalContribution.builder()
                        .id(UUID.randomUUID())
                        .goalId(UUID.randomUUID())
                        .contributionDate(LocalDate.now())
                        .amount(BigDecimal.TEN)
                        .build();
        when(contributionRepo.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.deleteContribution(userId, g.getId(), foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(contributionRepo, never()).delete(any());
    }

    @Test
    void deleteContributionRemovesMatch() {
        SavingsGoal g = goal("1000", null);
        when(goalRepo.findByIdAndUserId(g.getId(), userId)).thenReturn(Optional.of(g));
        SavingsGoalContribution c =
                SavingsGoalContribution.builder()
                        .id(UUID.randomUUID())
                        .goalId(g.getId())
                        .contributionDate(LocalDate.now())
                        .amount(BigDecimal.TEN)
                        .build();
        when(contributionRepo.findById(c.getId())).thenReturn(Optional.of(c));

        service.deleteContribution(userId, g.getId(), c.getId());

        verify(contributionRepo).delete(c);
    }

    @Test
    void listContributionsRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(goalRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listContributions(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
