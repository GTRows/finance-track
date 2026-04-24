package com.fintrack.admin.dto;

import java.time.Instant;

/** Metadata about a single log file. */
public record LogFileInfo(String name, long sizeBytes, Instant lastModified, boolean compressed) {}
