package com.example.ExpenseTracker.controller;

import com.example.ExpenseTracker.DTO.TransactionDTO.CategoryRequest;
import com.example.ExpenseTracker.DTO.TransactionDTO.CategoryResponse;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Shared default categories plus categories you create")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) ETransactionType kind) {
        return ResponseEntity.ok(categoryService.list(userId, kind));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        categoryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
