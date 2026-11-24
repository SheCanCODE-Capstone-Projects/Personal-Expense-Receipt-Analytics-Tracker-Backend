package com.example.ExpenseTracker.core;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public final class ForecastCalculator {

    private ForecastCalculator() {}

    public record Result(
            YearMonth month,
            LocalDate asOf,
            int daysElapsed,
            int daysInMonth,
            int remainingDays,
            long spentSoFar,
            long averageDailySpend,
            long projectedTotalExpense,
            long projectedRemainingExpense,
            String explanation) {}

    public static Result calculate(
            YearMonth month,
            LocalDate asOf,
            Map<LocalDate, Long> dailyExpenses) {

        YearMonth targetMonth = (month != null) ? month : YearMonth.now();
        int daysInMonth = targetMonth.lengthOfMonth();

        LocalDate effectiveDate = resolveEffectiveDate(targetMonth, asOf);
        int daysElapsed = effectiveDate.getDayOfMonth();
        int remainingDays = Math.max(0, daysInMonth - daysElapsed);

        long spentSoFar = sumMonthExpenses(targetMonth, effectiveDate, dailyExpenses);

        // Integer division (rounded down to whole currency units) to avoid floating point
        long averageDailySpend = daysElapsed > 0 ? (spentSoFar / daysElapsed) : 0L;
        long projectedRemainingExpense = averageDailySpend * remainingDays;
        long projectedTotalExpense = spentSoFar + projectedRemainingExpense;

        String explanation = String.format(
                "Spent %d across %d day(s) (avg %d/day); projected %d more over %d remaining day(s) = %d total.",
                spentSoFar,
                daysElapsed,
                averageDailySpend,
                projectedRemainingExpense,
                remainingDays,
                projectedTotalExpense
        );

        return new Result(
                targetMonth,
                effectiveDate,
                daysElapsed,
                daysInMonth,
                remainingDays,
                spentSoFar,
                averageDailySpend,
                projectedTotalExpense,
                projectedRemainingExpense,
                explanation
        );
    }

    private static LocalDate resolveEffectiveDate(YearMonth month, LocalDate asOf) {
        LocalDate startOfMonth = month.atDay(1);
        LocalDate endOfMonth = month.atEndOfMonth();

        if (asOf == null) {
            return endOfMonth;
        }
        if (asOf.isBefore(startOfMonth)) {
            return startOfMonth;
        }
        if (asOf.isAfter(endOfMonth)) {
            return endOfMonth;
        }
        return asOf;
    }

    private static long sumMonthExpenses(
            YearMonth month,
            LocalDate upToDate,
            Map<LocalDate, Long> dailyExpenses) {

        if (dailyExpenses == null || dailyExpenses.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        for (Map.Entry<LocalDate, Long> entry : dailyExpenses.entrySet()) {
            LocalDate date = entry.getKey();
            Long amount = entry.getValue();
            if (date == null || amount == null || amount <= 0L) {
                continue;
            }
            if (YearMonth.from(date).equals(month) && !date.isAfter(upToDate)) {
                total += amount;
            }
        }
        return total;
    }
}