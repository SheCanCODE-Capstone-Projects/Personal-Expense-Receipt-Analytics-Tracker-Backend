package com.example.ExpenseTracker.DTO;

import com.example.ExpenseTracker.Model.ETransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class TransactionDTO {
        private TransactionDTO() {}

        public record TransactionRequest(
                @NotNull ETransactionType type,
                @NotNull @Positive Long amount,
                String currency,
                @NotNull LocalDate date,
                UUID categoryId,
                UUID accountId,
                @Size(max = 150) String merchant,
                @Size(max = 50) String paymentMethod,
                @Size(max = 500) String note,
                Boolean recurring) {

                public boolean isRecurring() {
                        return Boolean.TRUE.equals(recurring);
                }
        }

        public record TransactionResponse(
                UUID id, ETransactionType type, Long amount, String currency, LocalDate date,
                UUID categoryId, String categoryName, UUID accountId, String merchant,
                String paymentMethod, String note, boolean recurring, String source,
                boolean possibleDuplicate) {}

        public record CategoryRequest(@NotBlank @Size(max = 100) String name, @NotNull ETransactionType kind) {}

        public record CategoryResponse(UUID id, String name, ETransactionType kind, boolean shared) {}

        public record AccountRequest(@NotNull String name, String kind) {}

        public record AccountResponse(UUID id, String name, String kind, String currency) {}

        public record GenerateRecurringResult(int created, int skippedAlreadyExisting) {}
}