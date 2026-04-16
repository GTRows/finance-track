package com.fintrack.savings;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.*;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import com.fintrack.savings.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalService {

    private static final int PACE_WINDOW_DAYS = 90;

    private final SavingsGoalRepository goalRepo;
    private final SavingsContributionRepository contributionRepo;
    private final PortfolioRepository portfolioRepo;
    private final HoldingRepository holdingRepo;
    private final AssetRepository assetRepo;
    private final SnapshotRepository snapshotRepo;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        List<SavingsGoal> goals = goalRepo.findActive(userId);
        return goals.stream().map(g -> toResponse(userId, g)).toList();
    }

    @Transactional
    public GoalResponse create(UUID userId, UpsertGoalRequest req) {
        validatePortfolio(userId, req.linkedPortfolioId());
        SavingsGoal goal = SavingsGoal.builder()
                .userId(userId)
                .name(req.name())
                .targetAmount(req.targetAmount())
                .targetDate(req.targetDate())
                .linkedPortfolioId(req.linkedPortfolioId())
                .notes(req.notes())
                .build();
        goal = goalRepo.save(goal);
        log.info("Savings goal created: id={} name={}", goal.getId(), goal.getName());
        return toResponse(userId, goal);
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, UpsertGoalRequest req) {
        SavingsGoal goal = requireOwned(userId, goalId);
        validatePortfolio(userId, req.linkedPortfolioId());
        goal.setName(req.name());
        goal.setTargetAmount(req.targetAmount());
        goal.setTargetDate(req.targetDate());
        goal.setLinkedPortfolioId(req.linkedPortfolioId());
        goal.setNotes(req.notes());
        return toResponse(userId, goal);
    }

    @Transactional
    public void archive(UUID userId, UUID goalId) {
        SavingsGoal goal = requireOwned(userId, goalId);
        goal.setArchivedAt(Instant.now());
        log.info("Savings goal archived: id={}", goalId);
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> listContributions(UUID userId, UUID goalId) {
        requireOwned(userId, goalId);
        return contributionRepo.findByGoalIdOrderByContributionDateDesc(goalId).stream()
                .map(ContributionResponse::from)
                .toList();
    }

    @Transactional
    public ContributionResponse addContribution(UUID userId, UUID goalId, ContributionRequest req) {
        requireOwned(userId, goalId);
        SavingsGoalContribution contribution = SavingsGoalContribution.builder()
                .goalId(goalId)
                .contributionDate(req.contributionDate())
                .amount(req.amount())
                .note(req.note())
                .build();
        contribution = contributionRepo.save(contribution);
        return ContributionResponse.from(contribution);
    }

    @Transactional
    public void deleteContribution(UUID userId, UUID goalId, UUID contributionId) {
        requireOwned(userId, goalId);
        SavingsGoalContribution c = contributionRepo.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found"));
        if (!c.getGoalId().equals(goalId)) {
            throw new ResourceNotFoundException("Contribution not found");
        }
        contributionRepo.delete(c);
    }

    private SavingsGoal requireOwned(UUID userId, UUID goalId) {
        return goalRepo.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found"));
    }

    private void validatePortfolio(UUID userId, UUID portfolioId) {
        if (portfolioId == null) return;
        portfolioRepo.findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked portfolio not found"));
    }

    private GoalResponse toResponse(UUID userId, SavingsGoal goal) {
        ProgressData progress = computeProgress(goal);
        BigDecimal ratio = goal.getTargetAmount().signum() > 0
                ? progress.current.divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String status = ratio.compareTo(BigDecimal.ONE) >= 0 ? "REACHED" : "IN_PROGRESS";

        BigDecimal remaining = goal.getTargetAmount().subtract(progress.current).max(BigDecimal.ZERO);
        LocalDate projected = null;
        if ("IN_PROGRESS".equals(status)
                && progress.monthlyPace != null
                && progress.monthlyPace.signum() > 0) {
            BigDecimal monthsNeeded = remaining.divide(progress.monthlyPace, 2, RoundingMode.UP);
            long days = monthsNeeded.multiply(BigDecimal.valueOf(30)).longValueExact();
            projected = LocalDate.now().plusDays(days);
        }

        String linkedName = null;
        if (goal.getLinkedPortfolioId() != null) {
            linkedName = portfolioRepo.findByIdAndUserIdAndActiveTrue(goal.getLinkedPortfolioId(), userId)
                    .map(Portfolio::getName)
                    .orElse(null);
        }

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getTargetDate(),
                goal.getLinkedPortfolioId(),
                linkedName,
                goal.getNotes(),
                progress.current,
                ratio,
                progress.monthlyPace,
                projected,
                status
        );
    }

    private ProgressData computeProgress(SavingsGoal goal) {
        if (goal.getLinkedPortfolioId() != null) {
            return progressFromPortfolio(goal.getLinkedPortfolioId());
        }
        return progressFromContributions(goal.getId());
    }

    private ProgressData progressFromPortfolio(UUID portfolioId) {
        List<PortfolioHolding> holdings = holdingRepo.findByPortfolioId(portfolioId);
        BigDecimal current = BigDecimal.ZERO;
        if (!holdings.isEmpty()) {
            Set<UUID> ids = new HashSet<>();
            holdings.forEach(h -> ids.add(h.getAssetId()));
            Map<UUID, Asset> assets = new HashMap<>();
            assetRepo.findAllById(ids).forEach(a -> assets.put(a.getId(), a));
            for (PortfolioHolding h : holdings) {
                Asset a = assets.get(h.getAssetId());
                if (a == null || a.getPrice() == null) continue;
                current = current.add(a.getPrice().multiply(h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO));
            }
        }

        BigDecimal pace = paceFromSnapshots(portfolioId);
        return new ProgressData(current, pace);
    }

    private ProgressData progressFromContributions(UUID goalId) {
        BigDecimal current = contributionRepo.sumByGoalId(goalId);
        BigDecimal pace = paceFromContributions(goalId);
        return new ProgressData(current, pace);
    }

    private BigDecimal paceFromSnapshots(UUID portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        if (snapshots.size() < 2) return null;

        LocalDate cutoff = LocalDate.now().minusDays(PACE_WINDOW_DAYS);
        PortfolioSnapshot start = null;
        for (PortfolioSnapshot s : snapshots) {
            if (!s.getSnapshotDate().isBefore(cutoff)) {
                start = s;
                break;
            }
        }
        if (start == null) start = snapshots.get(0);

        PortfolioSnapshot end = snapshots.get(snapshots.size() - 1);
        if (start == end) return null;

        BigDecimal delta = end.getTotalValueTry().subtract(start.getTotalValueTry());
        long days = ChronoUnit.DAYS.between(start.getSnapshotDate(), end.getSnapshotDate());
        if (days <= 0) return null;

        BigDecimal perDay = delta.divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
        return perDay.multiply(BigDecimal.valueOf(30));
    }

    private BigDecimal paceFromContributions(UUID goalId) {
        List<SavingsGoalContribution> contributions = contributionRepo.findByGoalIdOrderByContributionDateDesc(goalId);
        if (contributions.isEmpty()) return null;

        LocalDate cutoff = LocalDate.now().minusDays(PACE_WINDOW_DAYS);
        BigDecimal windowed = BigDecimal.ZERO;
        for (SavingsGoalContribution c : contributions) {
            if (!c.getContributionDate().isBefore(cutoff)) {
                windowed = windowed.add(c.getAmount());
            }
        }
        if (windowed.signum() <= 0) return null;
        return windowed.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    private record ProgressData(BigDecimal current, BigDecimal monthlyPace) {}
}
