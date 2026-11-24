package com.example.ExpenseTracker.Model;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
    @Table(name = "shared_expenses")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
public class SharedExpense {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "group_id", nullable = false)
        private Group group;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payer_id", nullable = false)
        private User payer;

        @Column(nullable = false)
        private String description;

        @Column(nullable = false, precision = 19, scale = 2)
        private Long totalAmount;

        @Column(nullable = false)
        private String currency;

        private LocalDateTime expenseDate;
        @OneToMany(mappedBy = "sharedExpense")
        private List<ExpenseShare> shares;
    };


