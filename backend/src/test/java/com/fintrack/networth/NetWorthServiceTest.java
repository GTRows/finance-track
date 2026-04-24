package com.fintrack.networth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.NetWorthEvent;
import com.fintrack.common.entity.NetWorthEvent.EventType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.networth.dto.NetWorthEventResponse;
import com.fintrack.networth.dto.NetWorthTimelineResponse;
import com.fintrack.networth.dto.UpsertEventRequest;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
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
class NetWorthServiceTest {

    @Mock NetWorthEventRepository eventRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock SnapshotRepository snapshotRepo;

    @InjectMocks NetWorthService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio(String name) {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .active(true)
                .build();
    }

    private PortfolioSnapshot snap(UUID portfolioId, LocalDate date, String value, String cost) {
        return PortfolioSnapshot.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .snapshotDate(date)
                .totalValueTry(new BigDecimal(value))
                .totalCostTry(new BigDecimal(cost))
                .build();
    }

    @Test
    void timelineReturnsEmptyWhenNoPortfolios() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(eventRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());

        NetWorthTimelineResponse res = service.timeline(userId);

        assertThat(res.series()).isEmpty();
        assertThat(res.events()).isEmpty();
    }

    @Test
    void timelineSumsValuesPerDateAcrossPortfolios() {
        Portfolio p1 = portfolio("A");
        Portfolio p2 = portfolio("B");
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p1, p2));
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p1.getId()))
                .thenReturn(
                        List.of(
                                snap(p1.getId(), LocalDate.of(2026, 4, 1), "100", "80"),
                                snap(p1.getId(), LocalDate.of(2026, 4, 2), "110", "80")));
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p2.getId()))
                .thenReturn(
                        List.of(
                                snap(p2.getId(), LocalDate.of(2026, 4, 1), "50", "40"),
                                snap(p2.getId(), LocalDate.of(2026, 4, 3), "70", "45")));
        when(eventRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());

        NetWorthTimelineResponse res = service.timeline(userId);

        assertThat(res.series()).hasSize(3);
        assertThat(res.series().get(0).date()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(res.series().get(0).totalValueTry()).isEqualByComparingTo("150");
        assertThat(res.series().get(0).totalCostTry()).isEqualByComparingTo("120");
        assertThat(res.series().get(1).totalValueTry()).isEqualByComparingTo("110");
        assertThat(res.series().get(2).totalValueTry()).isEqualByComparingTo("70");
    }

    @Test
    void timelineHandlesNullValueAndCostFields() {
        Portfolio p = portfolio("A");
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p.getId()))
                .thenReturn(
                        List.of(
                                PortfolioSnapshot.builder()
                                        .id(UUID.randomUUID())
                                        .portfolioId(p.getId())
                                        .snapshotDate(LocalDate.of(2026, 4, 1))
                                        .totalValueTry(null)
                                        .totalCostTry(null)
                                        .build()));
        when(eventRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());

        NetWorthTimelineResponse res = service.timeline(userId);

        assertThat(res.series()).hasSize(1);
        assertThat(res.series().get(0).totalValueTry()).isEqualByComparingTo("0");
        assertThat(res.series().get(0).totalCostTry()).isEqualByComparingTo("0");
    }

    @Test
    void timelineIncludesEvents() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        NetWorthEvent ev =
                NetWorthEvent.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .eventDate(LocalDate.of(2026, 4, 10))
                        .eventType(EventType.MILESTONE)
                        .label("1M")
                        .impactTry(BigDecimal.ZERO)
                        .build();
        when(eventRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of(ev));

        NetWorthTimelineResponse res = service.timeline(userId);

        assertThat(res.events()).hasSize(1);
        assertThat(res.events().get(0).label()).isEqualTo("1M");
    }

    @Test
    void listEventsReturnsMappedRows() {
        NetWorthEvent ev =
                NetWorthEvent.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .eventDate(LocalDate.of(2026, 4, 1))
                        .eventType(EventType.PURCHASE)
                        .label("Car")
                        .impactTry(new BigDecimal("-500000"))
                        .build();
        when(eventRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of(ev));

        List<NetWorthEventResponse> res = service.listEvents(userId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).label()).isEqualTo("Car");
        assertThat(res.get(0).impactTry()).isEqualByComparingTo("-500000");
    }

    @Test
    void createPersistsAndParsesEventType() {
        when(eventRepo.save(any(NetWorthEvent.class)))
                .thenAnswer(
                        inv -> {
                            NetWorthEvent e = inv.getArgument(0);
                            e.setId(UUID.randomUUID());
                            return e;
                        });

        NetWorthEventResponse res =
                service.create(
                        userId,
                        new UpsertEventRequest(
                                LocalDate.of(2026, 4, 1),
                                "income",
                                "Bonus",
                                "annual",
                                new BigDecimal("25000")));

        ArgumentCaptor<NetWorthEvent> captor = ArgumentCaptor.forClass(NetWorthEvent.class);
        verify(eventRepo).save(captor.capture());
        NetWorthEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEventType()).isEqualTo(EventType.INCOME);
        assertThat(saved.getLabel()).isEqualTo("Bonus");
        assertThat(res.impactTry()).isEqualByComparingTo("25000");
    }

    @Test
    void createFallsBackToNoteForUnknownEventType() {
        when(eventRepo.save(any(NetWorthEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                userId,
                new UpsertEventRequest(LocalDate.of(2026, 4, 1), "not-a-type", "x", null, null));

        ArgumentCaptor<NetWorthEvent> captor = ArgumentCaptor.forClass(NetWorthEvent.class);
        verify(eventRepo).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.NOTE);
    }

    @Test
    void updateRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(eventRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpsertEventRequest(
                                                LocalDate.now(), "NOTE", "x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMutatesAllFields() {
        NetWorthEvent existing =
                NetWorthEvent.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .eventDate(LocalDate.of(2026, 1, 1))
                        .eventType(EventType.NOTE)
                        .label("Old")
                        .note("old")
                        .impactTry(BigDecimal.ZERO)
                        .build();
        when(eventRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                existing.getId(),
                new UpsertEventRequest(
                        LocalDate.of(2026, 4, 1),
                        "MILESTONE",
                        "New",
                        "new note",
                        new BigDecimal("123")));

        assertThat(existing.getEventDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(existing.getEventType()).isEqualTo(EventType.MILESTONE);
        assertThat(existing.getLabel()).isEqualTo("New");
        assertThat(existing.getNote()).isEqualTo("new note");
        assertThat(existing.getImpactTry()).isEqualByComparingTo("123");
    }

    @Test
    void deleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(eventRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepo, never()).delete(any());
    }

    @Test
    void deleteRemovesWhenOwned() {
        NetWorthEvent ev =
                NetWorthEvent.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .eventDate(LocalDate.now())
                        .eventType(EventType.NOTE)
                        .label("x")
                        .impactTry(BigDecimal.ZERO)
                        .build();
        when(eventRepo.findByIdAndUserId(ev.getId(), userId)).thenReturn(Optional.of(ev));

        service.delete(userId, ev.getId());

        verify(eventRepo).delete(ev);
    }
}
