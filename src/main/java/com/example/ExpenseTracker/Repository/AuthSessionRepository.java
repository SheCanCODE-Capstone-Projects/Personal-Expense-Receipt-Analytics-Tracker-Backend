package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findByAccessHashAndAccessExpiresAtAfter(String hash, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshHash(String hash);

    Optional<AuthSession> findByAccessHash(String hash);

    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId")
    int deleteAllForUser(UUID userId);

    @Modifying
    @Query("delete from AuthSession s where s.refreshExpiresAt < :now")
    int deleteExpired(Instant now);
}
