package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ETransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrderByTypeAscNameAsc(Long userId);
    Optional<Category> findByIdAndUserId(Long id, Long userId);
    Optional<Category> findByUserIdAndNameIgnoreCaseAndType(Long userId, String name, ETransactionType type);
}