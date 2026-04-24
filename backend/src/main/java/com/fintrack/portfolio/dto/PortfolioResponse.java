package com.fintrack.portfolio.dto;

import com.fintrack.common.entity.Portfolio;
import java.time.Instant;
import java.util.UUID;

/** Portfolio response sent to the client. */
public record PortfolioResponse(
        UUID id, String name, Portfolio.PortfolioType type, String description, Instant createdAt) {

    /** Maps a {@link Portfolio} entity to a response DTO. */
    public static PortfolioResponse from(Portfolio p) {
        return new PortfolioResponse(
                p.getId(), p.getName(), p.getPortfolioType(), p.getDescription(), p.getCreatedAt());
    }
}
