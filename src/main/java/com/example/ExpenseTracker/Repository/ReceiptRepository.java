package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
    List<Receipt> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<Receipt> findByIdAndUser_Id(UUID id, UUID userId);
}