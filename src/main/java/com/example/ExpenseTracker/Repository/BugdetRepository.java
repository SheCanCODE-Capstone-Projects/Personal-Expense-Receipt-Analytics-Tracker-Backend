package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugdetRepository extends JpaRepository<Budget,Long> {
}
