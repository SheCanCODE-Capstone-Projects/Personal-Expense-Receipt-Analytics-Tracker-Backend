package com.example.ExpenseTracker.DTO;

import com.example.ExpenseTracker.core.ForecastCalculator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public final class BudgetDTO {
    private BudgetDTO() {}

    public record BudgetRequest(
            @NotNull @Pattern(regexp = "\\d{4}-\\d{2}", message = "must look like 2026-11") String month,
            UUID categoryId,
            @NotNull @Positive Long limitAmount,
            @Min(1) @Max(100) Integer warnPercent) {}

    public record BudgetStatus(
            UUID id, String month, UUID categoryId, String categoryName,
            long limitAmount, long spent, long remaining, int percentUsed, int warnPercent,
            String status, String message) {}

    public record CategoryTotal(UUID categoryId, String categoryName, long amount, int percentOfExpenses) {}

    public record DashboardResponse(
            String month, String currency,
            long incomeTotal, long expenseTotal, long net, int transactionCount,
            List<CategoryTotal> expensesByCategory,
            List<BudgetStatus> budgets,
            ForecastCalculator.Result forecast) {}
}