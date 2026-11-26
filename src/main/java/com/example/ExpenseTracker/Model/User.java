package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter the correct email format")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(name = "hash_password", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String hashPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ERole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user")
    private List<Account> accounts;

    @OneToMany(mappedBy = "user")
    private List<Transaction> transactions;

    @OneToMany(mappedBy = "user")
    private List<Budget> budgets;

    @OneToMany(mappedBy = "user")
    private List<Category> categories;

    @OneToMany(mappedBy = "user")
    private List<Receipt> receipts;

    @OneToMany(mappedBy = "user")
    private List<ImportBatch> importBatches;

    @OneToMany(mappedBy = "createdBy")
    private List<Group> groups;

    @OneToMany(mappedBy = "user")
    private List<Membership> memberships;

    @OneToMany(mappedBy = "payer")
    private List<SharedExpense> paidSharedExpenses;

    @OneToMany(mappedBy = "fromUser")
    private List<Settlement> sentSettlements;

    @OneToMany(mappedBy = "toUser")
    private List<Settlement> receivedSettlements;

    @OneToMany(mappedBy = "user")
    private List<ExpenseShare> expenseShares;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}






