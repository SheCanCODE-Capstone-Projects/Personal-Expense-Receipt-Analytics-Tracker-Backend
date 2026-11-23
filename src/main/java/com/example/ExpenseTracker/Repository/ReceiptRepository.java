package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt,Long> {
    List<Receipt> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Receipt> findByIdAndUserId(Long id, Long userId);
    Optional<Receipt> findFirstByImportBatchId(Long importBatchId);

    Optional<Receipt> findByTransactionIdAndUserId(Long transactionId, Long userId);
}
