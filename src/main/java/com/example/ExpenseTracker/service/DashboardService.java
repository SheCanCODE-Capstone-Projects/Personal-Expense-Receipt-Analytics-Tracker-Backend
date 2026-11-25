package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetStatus;
import com.example.ExpenseTracker.DTO.BudgetDTO.CategoryTotal;
import com.example.ExpenseTracker.DTO.BudgetDTO.DashboardResponse;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Budget;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import com.example.ExpenseTracker.Repository.BudgetRepository;
import com.example.ExpenseTracker.Repository.TransactionRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.core.ForecastCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DEFAULT_WARN_PERCENT = 80;

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public DashboardService(
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    public DashboardResponse getMonthlyDashboard(UUID userId, String monthParam, String currencyParam) {


        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        YearMonth ym = parseYearMonth(monthParam);
        String normalizedMonth = ym.toString();
        String currency = resolveCurrency(currencyParam);

        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<Transaction> monthTransactions = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(userId, start, end)
                .stream()
                .filter(tx -> currency.equalsIgnoreCase(tx.getCurrency()))
                .toList();

        long incomeTotal = 0L;
        long expenseTotal = 0L;
        Map<UUID, Long> expenseByCategoryId = new HashMap<>();
        Map<UUID, String> categoryNames = new HashMap<>();
        Map<LocalDate, Long> dailyExpenses = new HashMap<>();

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        for (Transaction tx : monthTransactions) {
            long amount = tx.getAmount() != null ? tx.getAmount() : 0L;
            if (tx.getTransactionType() == ETransactionType.INCOME) {
                incomeTotal += amount;
            } else if (tx.getTransactionType() == ETransactionType.EXPENSE) {
                expenseTotal += amount;

                if (tx.getCategory() != null) {
                    UUID catId = tx.getCategory().getId();
                    categoryNames.putIfAbsent(catId, tx.getCategory().getName());
                    expenseByCategoryId.merge(catId, amount, Long::sum);
                }

                if (tx.getTransactionDate() != null) {
                    LocalDate txDate = tx.getTransactionDate().toLocalDate();
                    if (!txDate.isBefore(monthStart) && !txDate.isAfter(monthEnd)) {
                        dailyExpenses.merge(txDate, amount, Long::sum);
                    }
                }
            }
        }

        long net = incomeTotal - expenseTotal;
        final long totalExpenses = expenseTotal;
        List<CategoryTotal> expensesByCategory = buildCategoryTotals(expenseByCategoryId, categoryNames, totalExpenses);

        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(
                userId, ym.getMonthValue(), ym.getYear());
        List<BudgetStatus> budgetStatuses = budgets.stream()
                .map(b -> toBudgetStatus(b, normalizedMonth, totalExpenses, expenseByCategoryId))
                .toList();

        LocalDate asOf = resolveAsOfDate(ym);
        ForecastCalculator.Result forecast = ForecastCalculator.calculate(ym, asOf, dailyExpenses);

        return new DashboardResponse(
                normalizedMonth,
                currency,
                incomeTotal,
                expenseTotal,
                net,
                monthTransactions.size(),
                expensesByCategory,
                budgetStatuses,
                forecast
        );
    }

    private YearMonth parseYearMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format, e.g. 2026-11");
        }
    }

    private String resolveCurrency(String currencyParam) {
        return (currencyParam == null || currencyParam.isBlank())
                ? "RWF"
                : currencyParam.trim().toUpperCase();
    }

    private LocalDate resolveAsOfDate(YearMonth ym) {
        LocalDate today = LocalDate.now();
        boolean isCurrentMonth = today.getYear() == ym.getYear() && today.getMonthValue() == ym.getMonthValue();
        return isCurrentMonth ? today : ym.atEndOfMonth();
    }

    private List<CategoryTotal> buildCategoryTotals(
            Map<UUID, Long> expenseByCategoryId,
            Map<UUID, String> categoryNames,
            long totalExpenses) {

        return expenseByCategoryId.entrySet().stream()
                .map(entry -> {
                    UUID catId = entry.getKey();
                    long amount = entry.getValue();
                    int percentOfExpenses = totalExpenses > 0
                            ? (int) ((amount * 100L) / totalExpenses)
                            : 0;
                    return new CategoryTotal(catId, categoryNames.get(catId), amount, percentOfExpenses);
                })
                .sorted(Comparator.comparingLong(CategoryTotal::amount).reversed())
                .toList();
    }

    private BudgetStatus toBudgetStatus(
            Budget budget,
            String monthStr,
            long totalExpense,
            Map<UUID, Long> expenseByCategoryId) {

        long limit = budget.getAmountLimit() != null ? budget.getAmountLimit() : 0L;
        UUID categoryId = budget.getCategory() != null ? budget.getCategory().getId() : null;
        String categoryName = budget.getCategory() != null ? budget.getCategory().getName() : "Overall Monthly";

        long spent = categoryId != null
                ? expenseByCategoryId.getOrDefault(categoryId, 0L)
                : totalExpense;

        long remaining = limit - spent;
        int percentUsed = limit > 0 ? (int) ((spent * 100L) / limit) : 0;
        int warnPercent = DEFAULT_WARN_PERCENT;

        String status;
        String message;
        if (spent > limit) {
            status = "EXCEEDED";
            message = "Budget exceeded by " + (spent - limit);
        } else if (percentUsed >= warnPercent) {
            status = "WARNING";
            message = "Used " + percentUsed + "% of budget (" + remaining + " remaining)";
        } else {
            status = "OK";
            message = remaining + " remaining (" + percentUsed + "% used)";
        }

        return new BudgetStatus(
                budget.getId(),
                monthStr,
                categoryId,
                categoryName,
                limit,
                spent,
                remaining,
                percentUsed,
                warnPercent,
                status,
                message
        );
    }
}