package com.fintrack.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertTagRequest(
        @NotBlank @Size(max = 60) String name, @Size(max = 20) String color) {}
