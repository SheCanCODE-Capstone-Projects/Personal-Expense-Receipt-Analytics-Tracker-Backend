package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, UUID> {
    List<ExpenseShare> findBySharedExpenseIdIn(Collection<UUID> sharedExpenseIds);
}