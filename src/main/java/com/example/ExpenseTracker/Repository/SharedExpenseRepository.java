package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.SharedExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedExpenseRepository extends JpaRepository<SharedExpense, Long> {
}
