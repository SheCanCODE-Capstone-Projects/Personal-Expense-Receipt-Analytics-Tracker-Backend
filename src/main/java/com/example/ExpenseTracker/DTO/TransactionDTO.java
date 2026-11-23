package com.example.ExpenseTracker.DTO;
import com.example.ExpenseTracker.Model.ETransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class TransactionDTO {
        private TransactionDTO() {}
        public record TransactionRequest(
                @NotNull ETransactionType type,
                @NotNull @Positive Long amount,
                String currency,
                @NotNull LocalDate date,
                Long categoryId,
                Long Id,
                @Size(max = 150) String merchant,
                @Size(max = 50) String paymentMethod,
                @Size(max = 500) String note,
                boolean recurring) {}

        public record TransactionResponse(
                Long id, ETransactionType type, Long amount, String currency, LocalDate date,
                Long categoryId, String categoryName, Long Id, String merchant,
                String paymentMethod, String note, boolean recurring, String source,
                boolean possibleDuplicate) {}

        public record CategoryRequest(@NotNull String name, @NotNull ETransactionType kind) {}

        public record CategoryResponse(Long id, String name, ETransactionType kind) {}

        public record WalletRequest(@NotNull String name, String kind) {}

        public record WalletResponse(Long id, String name, String kind, String currency) {}

        public record GenerateRecurringResult(int created, int skippedAlreadyExisting) {}
    }


