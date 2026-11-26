package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ETransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByUserIdOrderByTypeAscNameAsc(UUID userId);
    Optional<Category> findByIdAndUserId(UUID id, UUID userId);
    Optional<Category> findByUserIdAndNameIgnoreCaseAndType(UUID userId, String name, ETransactionType type);
    Optional<Category> findByUserIsNullAndNameIgnoreCaseAndType(String name, ETransactionType type);

    // Shared defaults (user is null) plus the user's own categories.
    @Query("select c from Category c where c.user is null or c.user.id = :userId order by c.type, c.name")
    List<Category> findVisibleToUser(UUID userId);
}
