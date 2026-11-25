package com.example.ExpenseTracker.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String accessHash;
    @Column(unique = true, nullable = false)
    private String refreshHash;
    private Instant accessExpiresAt;
    private Instant refreshExpiresAt;
}
