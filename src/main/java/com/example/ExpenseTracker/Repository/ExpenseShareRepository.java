package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare,Long> {
}
