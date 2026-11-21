package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
}
