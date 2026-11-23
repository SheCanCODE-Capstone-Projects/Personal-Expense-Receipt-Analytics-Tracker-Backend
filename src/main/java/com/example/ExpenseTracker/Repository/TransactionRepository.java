package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction,Long> {
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

        List<Transaction> findByUserIdAndDateBetweenOrderByDateDescIdDesc(Long userId, LocalDate from, LocalDate to);

        List<Transaction> findByUserIdAndTypeAndCurrencyAndDateBetween(
                Long userId, ETransactionType type, String currency, LocalDate from, LocalDate to);

        List<Transaction> findByUserIdAndRecurringTrueAndDateBetween(Long userId, LocalDate from, LocalDate to);

    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            Long userId, LocalDateTime from, LocalDateTime to);

    List<Transaction> findByUserIdAndTypeAndCurrencyAndTransactionDateBetween(
            Long userId, ETransactionType type, String currency, LocalDateTime from, LocalDateTime to);

    List<Transaction> findByUserIdAndRecurringTrueAndTransactionDateBetween(
            Long userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndFingerprint(Long userId, String fingerprint);
    }


