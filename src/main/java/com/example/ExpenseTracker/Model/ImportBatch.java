package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "File name is required")
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @NotBlank(message = "File hash is required")
    @Column(name = "file_hash", nullable = false)
    private String fileHash;

    @NotNull(message = "Import status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "import_status", nullable = false)
    private EImportStatus importStatus = EImportStatus.PENDING;

    @PositiveOrZero(message = "Total rows cannot be negative")
    @Column(name = "total_rows")
    private Integer totalRows;

    @PositiveOrZero(message = "Imported rows cannot be negative")
    @Column(name = "imported_rows")
    private Integer importedRows;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "importBatch")
    private List<Transaction> transactions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (importStatus == null) {
            importStatus = EImportStatus.PENDING;
        }
    }
}