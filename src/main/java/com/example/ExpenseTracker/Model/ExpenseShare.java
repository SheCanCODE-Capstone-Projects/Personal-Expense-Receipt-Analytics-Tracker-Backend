package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "expense_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_expense_user",
                        columnNames = {"shared_expense_id", "user_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Share amount is required")
    @PositiveOrZero(message = "Share amount cannot be negative")
    @Column(name = "share_amount", nullable = false, precision = 19, scale = 2)
    private Long shareAmount;

    @DecimalMin(value = "0.00", message = "Share percentage cannot be less than 0")
    @DecimalMax(value = "100.00", message = "Share percentage cannot exceed 100")
    @Column(name = "share_percentage", precision = 5, scale = 2)
    private BigDecimal sharePercentage;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_expense_id", nullable = false)
    private SharedExpense sharedExpense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}