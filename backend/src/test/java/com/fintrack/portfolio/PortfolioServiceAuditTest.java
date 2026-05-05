package com.fintrack.portfolio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.Portfolio.PortfolioType;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.portfolio.dto.CreatePortfolioRequest;
import com.fintrack.portfolio.dto.UpdatePortfolioRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceAuditTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock AuditService auditService;

    @InjectMocks PortfolioService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio() {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Main")
                .portfolioType(PortfolioType.INDIVIDUAL)
                .active(true)
                .build();
    }

    @Test
    void createEmitsSuccessAudit() {
        when(portfolioRepository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(portfolioRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Portfolio p = inv.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });

        service.create(userId, new CreatePortfolioRequest("Main", PortfolioType.INDIVIDUAL, null));

        verify(auditService)
                .success(eq(AuditAction.PORTFOLIO_CREATED), eq(userId), any(), contains("id="));
    }

    @Test
    void createEmitsFailureAuditAtLimit() {
        when(portfolioRepository.countByUserIdAndActiveTrue(userId)).thenReturn(20L);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new CreatePortfolioRequest(
                                                "Main", PortfolioType.INDIVIDUAL, null)))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.PORTFOLIO_CREATED),
                        eq(userId),
                        any(),
                        contains("Portfolio limit"));
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    void updateEmitsSuccessAudit() {
        UUID id = UUID.randomUUID();
        Portfolio existing = portfolio();
        existing.setId(id);
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.of(existing));

        service.update(userId, id, new UpdatePortfolioRequest("New", "desc"));

        verify(auditService)
                .success(
                        eq(AuditAction.PORTFOLIO_UPDATED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void deleteEmitsSuccessAudit() {
        UUID id = UUID.randomUUID();
        Portfolio existing = portfolio();
        existing.setId(id);
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.of(existing));

        service.delete(userId, id);

        verify(auditService)
                .success(
                        eq(AuditAction.PORTFOLIO_DELETED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }
}
