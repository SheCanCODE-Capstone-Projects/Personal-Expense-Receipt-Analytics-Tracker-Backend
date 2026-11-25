package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUser_Id(UUID userId);
    Optional<Account> findByIdAndUser_Id(UUID id, UUID userId);
}
