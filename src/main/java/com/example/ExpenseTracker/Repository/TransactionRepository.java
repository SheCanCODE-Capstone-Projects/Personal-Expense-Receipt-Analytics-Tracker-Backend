package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
            UUID userId, LocalDateTime from, LocalDateTime to);

    List<Transaction> findByUserIdAndTransactionTypeAndCurrencyAndTransactionDateBetween(
            UUID userId, ETransactionType transactionType, String currency, LocalDateTime from, LocalDateTime to);

    List<Transaction> findByUserIdAndRecurringTrueAndTransactionDateBetween(
            UUID userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndFingerprint(UUID userId, String fingerprint);
}