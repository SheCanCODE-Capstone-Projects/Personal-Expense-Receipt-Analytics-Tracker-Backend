package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction,Long> {
}
