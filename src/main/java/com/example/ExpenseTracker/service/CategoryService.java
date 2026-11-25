package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.TransactionDTO.CategoryRequest;
import com.example.ExpenseTracker.DTO.TransactionDTO.CategoryResponse;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    public static final String FALLBACK_EXPENSE = "Other";
    public static final String FALLBACK_INCOME = "Other Income";

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, ETransactionType kind) {
        return categoryRepository.findVisibleToUser(userId).stream()
                .filter(c -> kind == null || c.getType() == kind)
                .map(CategoryService::toResponse)
                .toList();
    }

    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (categoryRepository.findByUserIsNullAndNameIgnoreCaseAndType(name, request.kind()).isPresent()
                || categoryRepository.findByUserIdAndNameIgnoreCaseAndType(userId, name, request.kind()).isPresent()) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Category category = new Category();
        category.setName(name);
        category.setType(request.kind());
        category.setUser(user);
        return toResponse(categoryRepository.save(category));
    }

    public void delete(UUID userId, UUID categoryId) {
        // Shared defaults are not owned by anyone, so they can't be deleted through this lookup.
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getTransactions() != null && !category.getTransactions().isEmpty()) {
            throw new IllegalArgumentException("Category is used by transactions and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    /** The shared catch-all category for a transaction type, used when no category is chosen. */
    public Category fallback(ETransactionType type) {
        String name = type == ETransactionType.INCOME ? FALLBACK_INCOME : FALLBACK_EXPENSE;
        return categoryRepository.findByUserIsNullAndNameIgnoreCaseAndType(name, type)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setType(type);
                    return categoryRepository.save(category);
                });
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getUser() == null);
    }
}
