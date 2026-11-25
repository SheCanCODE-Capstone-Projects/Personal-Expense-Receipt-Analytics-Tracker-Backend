package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    Optional<ImportBatch> findByIdAndUserId(UUID id, UUID userId);
    List<ImportBatch> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
