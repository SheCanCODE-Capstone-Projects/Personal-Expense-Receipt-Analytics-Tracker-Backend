package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch,Long> {
    Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);
    List<ImportBatch> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
