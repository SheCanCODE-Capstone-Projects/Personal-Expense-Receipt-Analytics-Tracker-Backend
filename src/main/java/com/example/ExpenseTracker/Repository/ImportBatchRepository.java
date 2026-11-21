package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch,Long> {
}
