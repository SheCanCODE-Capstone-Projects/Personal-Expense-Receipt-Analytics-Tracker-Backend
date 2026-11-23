package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare,Long> {
    List<ExpenseShare> findByGroupExpenseIdIn(Collection<Long> groupExpenseIds);
}
