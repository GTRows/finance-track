package com.fintrack.portfolio;

import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.Portfolio.PortfolioType;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.dto.CreatePortfolioRequest;
import com.fintrack.portfolio.dto.PortfolioResponse;
import com.fintrack.portfolio.dto.UpdatePortfolioRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioRepository portfolioRepository;

    @InjectMocks PortfolioService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio(String name) {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .portfolioType(PortfolioType.INDIVIDUAL)
                .active(true)
                .build();
    }

    @Test
    void listForUserReturnsMappedResponses() {
        when(portfolioRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(portfolio("Main"), portfolio("Crypto")));

        List<PortfolioResponse> res = service.listForUser(userId);

        assertThat(res).extracting(PortfolioResponse::name).containsExactly("Main", "Crypto");
    }

    @Test
    void getForUserThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForUser(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTrimsNameAndDescription() {
        when(portfolioRepository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(portfolioRepository.save(any())).thenAnswer(inv -> {
            Portfolio p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PortfolioResponse res = service.create(userId,
                new CreatePortfolioRequest("  Main  ", PortfolioType.INDIVIDUAL, "  note  "));

        assertThat(res.name()).isEqualTo("Main");
        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("note");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void createWithNullDescriptionKeepsNull() {
        when(portfolioRepository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreatePortfolioRequest("Main", PortfolioType.CRYPTO, null));

        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getPortfolioType()).isEqualTo(PortfolioType.CRYPTO);
    }

    @Test
    void createFailsAtLimit() {
        when(portfolioRepository.countByUserIdAndActiveTrue(userId)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(userId,
                new CreatePortfolioRequest("Main", PortfolioType.INDIVIDUAL, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Portfolio limit reached");

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void updateTrimsFieldsAndKeepsType() {
        UUID id = UUID.randomUUID();
        Portfolio existing = Portfolio.builder()
                .id(id).userId(userId)
                .name("Old").portfolioType(PortfolioType.STOCKS)
                .description("old").active(true).build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.of(existing));

        service.update(userId, id, new UpdatePortfolioRequest("  New  ", "  new desc  "));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("new desc");
        assertThat(existing.getPortfolioType()).isEqualTo(PortfolioType.STOCKS);
    }

    @Test
    void updateThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, id,
                new UpdatePortfolioRequest("New", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSoftDeletesWhenOwned() {
        UUID id = UUID.randomUUID();
        Portfolio existing = portfolio("Main");
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.of(existing));

        service.delete(userId, id);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deleteThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
