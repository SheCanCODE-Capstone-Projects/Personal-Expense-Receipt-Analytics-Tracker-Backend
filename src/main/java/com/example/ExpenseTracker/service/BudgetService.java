package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetRequest;
import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetStatus;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Budget;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.BudgetRepository;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.TransactionRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class BudgetService {

    private static final int DEFAULT_WARN_PERCENT = 80;

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public BudgetStatus saveBudget(Long userId, BudgetRequest request, String currencyParam) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        YearMonth ym = parseYearMonth(request.month());
        Category category = resolveCategory(userId, request.categoryId());
        Long categoryId = category != null ? category.getId() : null;

        Budget budget = (categoryId != null)
                ? budgetRepository.findByUserIdAndMonthAndYearAndCategoryId(
                        userId, ym.getMonthValue(), ym.getYear(), categoryId)
                .orElseGet(Budget::new)
                : budgetRepository.findByUserIdAndMonthAndYearAndCategoryIdIsNull(
                        userId, ym.getMonthValue(), ym.getYear())
                .orElseGet(Budget::new);

        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonth(ym.getMonthValue());
        budget.setYear(ym.getYear());
        budget.setAmountLimit(request.limitAmount());

        Budget saved = budgetRepository.save(budget);
        int warnPercent = request.warnPercent() != null ? request.warnPercent() : DEFAULT_WARN_PERCENT;
        String currency = resolveCurrency(currencyParam);

        return buildBudgetStatus(saved, ym, currency, warnPercent);
    }

    @Transactional(readOnly = true)
    public List<BudgetStatus> listByMonth(Long userId, String monthParam, String currencyParam) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        YearMonth ym = parseYearMonth(monthParam);
        String currency = resolveCurrency(currencyParam);

        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(
                userId, ym.getMonthValue(), ym.getYear());

        if (budgets.isEmpty()) {
            return List.of();
        }

        SpendSummary spendSummary = computeMonthSpend(userId, ym, currency);
        return budgets.stream()
                .map(b -> buildBudgetStatusWithSummary(b, ym, spendSummary, DEFAULT_WARN_PERCENT))
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetStatus getById(Long userId, Long budgetId, String currencyParam) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        YearMonth ym = YearMonth.of(budget.getYear(), budget.getMonth());
        String currency = resolveCurrency(currencyParam);
        return buildBudgetStatus(budget, ym, currency, DEFAULT_WARN_PERCENT);
    }

    public void delete(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
        budgetRepository.delete(budget);
    }

    private YearMonth parseYearMonth(String monthStr) {
        if (monthStr == null || monthStr.isBlank()) {
            throw new IllegalArgumentException("Month is required in YYYY-MM format");
        }
        try {
            return YearMonth.parse(monthStr.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format, e.g. 2026-11");
        }
    }

    private Category resolveCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Category not found");
        }
        return category;
    }

    private String resolveCurrency(String currencyParam) {
        return (currencyParam == null || currencyParam.isBlank())
                ? "RWF"
                : currencyParam.trim().toUpperCase();
    }

    private BudgetStatus buildBudgetStatus(
            Budget budget,
            YearMonth ym,
            String currency,
            int warnPercent) {
        SpendSummary summary = computeMonthSpend(budget.getUser().getId(), ym, currency);
        return buildBudgetStatusWithSummary(budget, ym, summary, warnPercent);
    }

    private BudgetStatus buildBudgetStatusWithSummary(
            Budget budget,
            YearMonth ym,
            SpendSummary summary,
            int warnPercent) {

        long limit = budget.getAmountLimit() != null ? budget.getAmountLimit() : 0L;
        Long categoryId = budget.getCategory() != null ? budget.getCategory().getId() : null;
        String categoryName = budget.getCategory() != null ? budget.getCategory().getName() : "Overall Monthly";

        long spent = categoryId != null
                ? summary.byCategoryId().getOrDefault(categoryId, 0L)
                : summary.totalExpense();

        long remaining = limit - spent;
        int percentUsed = limit > 0 ? (int) ((spent * 100L) / limit) : 0;

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
                ym.toString(),
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

    private SpendSummary computeMonthSpend(Long userId, YearMonth ym, String currency) {
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<Transaction> transactions = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(userId, start, end);

        long totalExpense = 0L;
        Map<Long, Long> byCategoryId = new HashMap<>();

        for (Transaction tx : transactions) {
            if (tx.getTransactionType() != ETransactionType.EXPENSE) {
                continue;
            }
            if (tx.getCurrency() != null && !currency.equalsIgnoreCase(tx.getCurrency())) {
                continue;
            }
            long amount = tx.getAmount() != null ? tx.getAmount() : 0L;
            totalExpense += amount;
            if (tx.getCategory() != null) {
                byCategoryId.merge(tx.getCategory().getId(), amount, Long::sum);
            }
        }

        return new SpendSummary(totalExpense, byCategoryId);
    }

    private record SpendSummary(long totalExpense, Map<Long, Long> byCategoryId) {}
}