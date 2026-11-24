package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    List<Receipt> findByUser_IdOrderByCreatedAtDesc(Long userId);
    Optional<Receipt> findByIdAndUser_Id(Long id, Long userId);
}