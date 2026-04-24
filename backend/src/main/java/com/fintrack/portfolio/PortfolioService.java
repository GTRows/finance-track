package com.fintrack.portfolio;

import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.dto.CreatePortfolioRequest;
import com.fintrack.portfolio.dto.PortfolioResponse;
import com.fintrack.portfolio.dto.UpdatePortfolioRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Portfolio business logic. All operations are scoped to the authenticated user. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private static final int MAX_PORTFOLIOS_PER_USER = 20;

    private final PortfolioRepository portfolioRepository;

    /** Lists all active portfolios for the given user. */
    @Transactional(readOnly = true)
    public List<PortfolioResponse> listForUser(UUID userId) {
        return portfolioRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                .map(PortfolioResponse::from)
                .toList();
    }

    /**
     * Returns a single portfolio belonging to the given user.
     *
     * @throws ResourceNotFoundException if the portfolio does not exist or is not owned by the user
     */
    @Transactional(readOnly = true)
    public PortfolioResponse getForUser(UUID userId, UUID portfolioId) {
        Portfolio portfolio =
                portfolioRepository
                        .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        return PortfolioResponse.from(portfolio);
    }

    /**
     * Creates a new portfolio for the given user.
     *
     * @throws BusinessRuleException if the user has reached the portfolio limit
     */
    @Transactional
    public PortfolioResponse create(UUID userId, CreatePortfolioRequest request) {
        long currentCount = portfolioRepository.countByUserIdAndActiveTrue(userId);
        if (currentCount >= MAX_PORTFOLIOS_PER_USER) {
            throw new BusinessRuleException(
                    "Portfolio limit reached (max " + MAX_PORTFOLIOS_PER_USER + ")",
                    "PORTFOLIO_LIMIT");
        }

        Portfolio portfolio =
                Portfolio.builder()
                        .userId(userId)
                        .name(request.name().trim())
                        .portfolioType(request.type())
                        .description(
                                request.description() != null ? request.description().trim() : null)
                        .active(true)
                        .build();

        portfolio = portfolioRepository.save(portfolio);
        log.info(
                "Portfolio created: id={} name={} userId={}",
                portfolio.getId(),
                portfolio.getName(),
                userId);
        return PortfolioResponse.from(portfolio);
    }

    /** Updates the name and description of an existing portfolio. Type is immutable. */
    @Transactional
    public PortfolioResponse update(UUID userId, UUID portfolioId, UpdatePortfolioRequest request) {
        Portfolio portfolio =
                portfolioRepository
                        .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        portfolio.setName(request.name().trim());
        portfolio.setDescription(
                request.description() != null ? request.description().trim() : null);

        log.info("Portfolio updated: id={} userId={}", portfolioId, userId);
        return PortfolioResponse.from(portfolio);
    }

    /** Soft-deletes a portfolio (sets active = false). */
    @Transactional
    public void delete(UUID userId, UUID portfolioId) {
        Portfolio portfolio =
                portfolioRepository
                        .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        portfolio.setActive(false);
        log.info("Portfolio deleted: id={} userId={}", portfolioId, userId);
    }
}
