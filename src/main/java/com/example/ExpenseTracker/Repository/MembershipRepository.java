package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ImportBatch;
import com.example.ExpenseTracker.Model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByGroupIdOrderById(Long groupId);
    List<Membership> findByUserId(Long userId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
