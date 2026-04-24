package com.fintrack.admin.dto;

/** Admin setting key-value pair with metadata. */
public record AdminSettingResponse(
        String key, String value, String description, String updatedAt) {}
