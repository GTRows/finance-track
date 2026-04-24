package com.fintrack.budget.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Bulk-edit transactions. All fields except {@code ids} are optional; at least one change field
 * must be present.
 *
 * <ul>
 *   <li>{@code categoryId} — if non-null, assigns this category to every selected transaction (must
 *       match the txn_type). Pass {@link java.util.UUID#fromString} with all zeros to clear the
 *       category.
 *   <li>{@code addTagIds} — tag ids to attach (ignored if already present).
 *   <li>{@code removeTagIds} — tag ids to detach.
 * </ul>
 */
public record BulkUpdateRequest(
        @NotEmpty List<UUID> ids,
        UUID categoryId,
        Boolean clearCategory,
        List<UUID> addTagIds,
        List<UUID> removeTagIds) {}
