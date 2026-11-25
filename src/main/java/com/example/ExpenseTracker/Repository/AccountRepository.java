package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
