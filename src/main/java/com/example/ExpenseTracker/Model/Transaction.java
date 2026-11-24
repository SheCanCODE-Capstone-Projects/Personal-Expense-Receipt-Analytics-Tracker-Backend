package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Column(nullable = false, precision = 19, scale = 2)
    private Long amount;

    @NotNull(message = "Transaction type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private ETransactionType transactionType;

    private String description;

    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private EMethod paymentMethod;

    @NotBlank(message = "Currency is required")
    @Column(nullable = false)
    private String currency = "RWF";

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency")
    private EReccuringFrequency recurrenceFrequency;

    @NotNull(message = "Transaction date is required")
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    private String fingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = true)
    private ImportBatch importBatch;

    @OneToMany(mappedBy = "transaction")
    private List<Receipt> receipts;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (currency == null || currency.isBlank()) {
            currency = "RWF";
        }
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
    }
}