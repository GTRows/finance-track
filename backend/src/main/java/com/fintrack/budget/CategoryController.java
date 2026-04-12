package com.fintrack.budget;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.dto.CategoriesResponse;
import com.fintrack.budget.dto.CategoryResponse;
import com.fintrack.budget.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<CategoriesResponse> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(categoryService.listAll(user.getId()));
    }

    @PostMapping("/income")
    public ResponseEntity<CategoryResponse> createIncome(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createIncome(user.getId(), request));
    }

    @PostMapping("/expense")
    public ResponseEntity<CategoryResponse> createExpense(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createExpense(user.getId(), request));
    }

    @PutMapping("/income/{id}")
    public ResponseEntity<CategoryResponse> updateIncome(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateIncome(user.getId(), id, request));
    }

    @PutMapping("/expense/{id}")
    public ResponseEntity<CategoryResponse> updateExpense(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateExpense(user.getId(), id, request));
    }

    @DeleteMapping("/income/{id}")
    public ResponseEntity<Void> deleteIncome(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        categoryService.deleteIncome(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/expense/{id}")
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        categoryService.deleteExpense(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
