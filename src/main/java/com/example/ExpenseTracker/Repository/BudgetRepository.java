package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget,Long> {
    List<Budget> findByUserIdAndMonth(Long userId, String month);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
    Optional<Budget> findByUserIdAndMonthAndCategoryId(Long userId, String month, Long categoryId);
    Optional<Budget> findByUserIdAndMonthAndCategoryIdIsNull(Long userId, String month);
}
