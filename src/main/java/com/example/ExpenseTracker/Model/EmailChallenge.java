package com.example.ExpenseTracker.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_challenges")
@Getter
@Setter
public class EmailChallenge {
    @Id
    private String email;
    private String codeHash;
    private Instant issuedAt;
    private int attempts;
}
