package com.example.ExpenseTracker.DTO;
import jakarta.validation.constraints.*;

import java.util.List;

public class BudgetDTO {
    private BudgetDTO() {}
    public record BudgetRequest(
            @NotNull @Pattern(regexp = "\\d{4}-\\d{2}", message = "must look like 2026-11") String month,
            Long categoryId,
            @NotNull @Positive Long limitAmount,
            @Min(1) @Max(100) Integer warnPercent) {}
    public record BudgetStatus(
            Long id, String month, Long categoryId, String categoryName,
            long limitAmount, long spent, long remaining, int percentUsed, int warnPercent,
            String status, String message) {}

    public record CategoryTotal(Long categoryId, String categoryName, long amount, int percentOfExpenses) {}

    public record DashboardResponse(
            String month, String currency,
            long incomeTotal, long expenseTotal, long net, int transactionCount,
            List<CategoryTotal> expensesByCategory,
            List<BudgetStatus> budgets,
            com.example.ExpenseTracker.core.ForecastCalculator.Result forecast) {}
}


