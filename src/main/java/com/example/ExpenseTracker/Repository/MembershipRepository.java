package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ImportBatch;
import com.example.ExpenseTracker.Model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    List<Membership> findByGroupIdOrderByJoinedAtAscIdAsc(UUID groupId);
    List<Membership> findByUserId(UUID userId);
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
