package com.fintrack.networth.dto;

import com.fintrack.common.entity.NetWorthEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record NetWorthEventResponse(
        UUID id,
        LocalDate eventDate,
        String eventType,
        String label,
        String note,
        BigDecimal impactTry
) {
    public static NetWorthEventResponse from(NetWorthEvent e) {
        return new NetWorthEventResponse(
                e.getId(),
                e.getEventDate(),
                e.getEventType().name(),
                e.getLabel(),
                e.getNote(),
                e.getImpactTry()
        );
    }
}
