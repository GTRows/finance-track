package com.fintrack.tag.dto;

import com.fintrack.common.entity.Tag;
import java.util.UUID;

public record TagResponse(UUID id, String name, String color, long usageCount) {
    public static TagResponse from(Tag tag, long usageCount) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), usageCount);
    }
}
