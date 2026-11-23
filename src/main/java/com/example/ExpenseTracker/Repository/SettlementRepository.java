package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository  extends JpaRepository<Settlement,Long> {
    List<Settlement> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
