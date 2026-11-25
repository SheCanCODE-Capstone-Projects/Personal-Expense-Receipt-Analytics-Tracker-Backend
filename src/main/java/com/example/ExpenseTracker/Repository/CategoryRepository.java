package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category,Long> {
}
