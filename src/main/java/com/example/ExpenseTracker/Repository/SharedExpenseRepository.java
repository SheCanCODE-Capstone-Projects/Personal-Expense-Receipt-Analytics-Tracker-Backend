
package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.SharedExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SharedExpenseRepository extends JpaRepository<SharedExpense, UUID> {
    List<SharedExpense> findByGroupIdOrderByExpenseDateDescIdDesc(UUID groupId);
}