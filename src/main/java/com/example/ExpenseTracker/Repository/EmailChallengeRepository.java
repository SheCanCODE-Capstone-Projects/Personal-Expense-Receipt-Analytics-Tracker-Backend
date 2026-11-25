package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.EmailChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface EmailChallengeRepository extends JpaRepository<EmailChallenge, String> {
    @Modifying
    @Query(value = "insert into email_challenges (email, attempts) values (:email, 0) on conflict (email) do nothing", nativeQuery = true)
    void ensureRow(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailChallenge> findByEmail(String email);
}
