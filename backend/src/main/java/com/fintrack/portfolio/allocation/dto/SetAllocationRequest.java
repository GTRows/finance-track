package com.fintrack.portfolio.allocation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SetAllocationRequest(@NotNull @Valid List<AllocationTargetInput> targets) {}
