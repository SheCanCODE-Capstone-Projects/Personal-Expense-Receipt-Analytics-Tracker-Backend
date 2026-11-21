package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "expense_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Group {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @NotBlank(message = "Group name is required")
        @Column(nullable = false)
        private String name;

        private String description;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "created_by", nullable = false)
        private User createdBy;

        @OneToMany(mappedBy = "group")
        private List<Membership> members;

        @OneToMany(mappedBy = "group")
        private List<SharedExpense> sharedExpenses;

        @OneToMany(mappedBy = "group")
        private List<Settlement> settlements;

        @PrePersist
        protected void onCreate() {
                createdAt = LocalDateTime.now();
        }
}