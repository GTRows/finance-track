package com.fintrack.portfolio.allocation.dto;

import java.math.BigDecimal;
import java.util.List;

public record AllocationSummary(
        BigDecimal totalValueTry, boolean configured, List<AllocationRow> rows) {}
