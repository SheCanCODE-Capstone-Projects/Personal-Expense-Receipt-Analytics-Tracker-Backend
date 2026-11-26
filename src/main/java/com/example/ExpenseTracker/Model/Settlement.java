package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Amount is required")
    @Positive(message = "Settlement amount must be greater than zero")
    @Column(nullable = false, precision = 19, scale = 2)
    private Long amount;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ESettlementStatus status = ESettlementStatus.PENDING;

    // Nullable so existing rows survive the schema update; null is read as RWF.
    @Column(length = 3)
    private String currency;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ESettlementStatus.PENDING;
        }
        if (status == ESettlementStatus.SETTLED && settledAt == null) {
            settledAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == ESettlementStatus.SETTLED && settledAt == null) {
            settledAt = LocalDateTime.now();
        }
    }
}