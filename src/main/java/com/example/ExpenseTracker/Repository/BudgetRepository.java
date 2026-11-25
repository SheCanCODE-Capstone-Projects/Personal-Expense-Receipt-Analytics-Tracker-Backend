package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
    Optional<Budget> findByUserIdAndMonthAndYearAndCategoryId(UUID userId, Integer month, Integer year, UUID categoryId);
    Optional<Budget> findByUserIdAndMonthAndYearAndCategoryIdIsNull(UUID userId, Integer month, Integer year);
}