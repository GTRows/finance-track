package com.fintrack.networth;

import com.fintrack.common.entity.NetWorthEvent;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.networth.dto.NetWorthEventResponse;
import com.fintrack.networth.dto.NetWorthTimelineResponse;
import com.fintrack.networth.dto.UpsertEventRequest;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetWorthService {

    private final NetWorthEventRepository eventRepo;
    private final PortfolioRepository portfolioRepo;
    private final SnapshotRepository snapshotRepo;

    @Transactional(readOnly = true)
    public NetWorthTimelineResponse timeline(UUID userId) {
        List<Portfolio> portfolios = portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);

        Map<LocalDate, BigDecimal[]> bucket = new TreeMap<>();
        for (Portfolio p : portfolios) {
            List<PortfolioSnapshot> snapshots = snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p.getId());
            for (PortfolioSnapshot s : snapshots) {
                BigDecimal[] acc = bucket.computeIfAbsent(
                        s.getSnapshotDate(),
                        k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                if (s.getTotalValueTry() != null) acc[0] = acc[0].add(s.getTotalValueTry());
                if (s.getTotalCostTry() != null) acc[1] = acc[1].add(s.getTotalCostTry());
            }
        }

        List<NetWorthTimelineResponse.Point> series = new ArrayList<>(bucket.size());
        for (Map.Entry<LocalDate, BigDecimal[]> e : bucket.entrySet()) {
            series.add(new NetWorthTimelineResponse.Point(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }

        List<NetWorthEventResponse> events = eventRepo.findByUserIdOrderByEventDateDesc(userId).stream()
                .map(NetWorthEventResponse::from)
                .toList();

        return new NetWorthTimelineResponse(series, events);
    }

    @Transactional(readOnly = true)
    public List<NetWorthEventResponse> listEvents(UUID userId) {
        return eventRepo.findByUserIdOrderByEventDateDesc(userId).stream()
                .map(NetWorthEventResponse::from)
                .toList();
    }

    @Transactional
    public NetWorthEventResponse create(UUID userId, UpsertEventRequest req) {
        NetWorthEvent event = NetWorthEvent.builder()
                .userId(userId)
                .eventDate(req.eventDate())
                .eventType(parseType(req.eventType()))
                .label(req.label())
                .note(req.note())
                .impactTry(req.impactTry())
                .build();
        event = eventRepo.save(event);
        log.info("Net worth event created: id={} date={} label={}", event.getId(), event.getEventDate(), event.getLabel());
        return NetWorthEventResponse.from(event);
    }

    @Transactional
    public NetWorthEventResponse update(UUID userId, UUID eventId, UpsertEventRequest req) {
        NetWorthEvent event = eventRepo.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        event.setEventDate(req.eventDate());
        event.setEventType(parseType(req.eventType()));
        event.setLabel(req.label());
        event.setNote(req.note());
        event.setImpactTry(req.impactTry());
        return NetWorthEventResponse.from(event);
    }

    @Transactional
    public void delete(UUID userId, UUID eventId) {
        NetWorthEvent event = eventRepo.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        eventRepo.delete(event);
        log.info("Net worth event deleted: id={}", eventId);
    }

    private NetWorthEvent.EventType parseType(String raw) {
        try {
            return NetWorthEvent.EventType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NetWorthEvent.EventType.NOTE;
        }
    }
}
