package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserIdAndMonthAndYear(Long userId, Integer month, Integer year);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
    Optional<Budget> findByUserIdAndMonthAndYearAndCategoryId(Long userId, Integer month, Integer year, Long categoryId);
    Optional<Budget> findByUserIdAndMonthAndYearAndCategoryIdIsNull(Long userId, Integer month, Integer year);
}