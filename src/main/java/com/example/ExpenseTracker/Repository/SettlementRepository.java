package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository  extends JpaRepository<Settlement,Long> {
}
