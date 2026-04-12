package com.fintrack.budget.dto;

import java.util.List;

public record CategoriesResponse(
        List<CategoryResponse> income,
        List<CategoryResponse> expense
) {
}
