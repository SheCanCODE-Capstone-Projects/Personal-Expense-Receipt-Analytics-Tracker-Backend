package com.example.ExpenseTracker.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Month-end forecast: projectedTotal = spentSoFar / daysElapsed * daysInMonth.
 * The average is kept exact and only the final projection is rounded (half-up to whole units),
 * so the result matches the formula instead of drifting by up to (daysRemaining - 1) units.
 */
public final class ForecastCalculator {

    private ForecastCalculator() {}

    public record Result(
            YearMonth month,
            LocalDate asOf,
            int daysElapsed,
            int daysInMonth,
            int remainingDays,
            long spentSoFar,
            BigDecimal averageDailySpend,
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

        BigDecimal spent = BigDecimal.valueOf(spentSoFar);
        BigDecimal averageDailySpend = spent.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        long projectedTotalExpense = spent.multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(daysElapsed), 0, RoundingMode.HALF_UP)
                .longValueExact();
        long projectedRemainingExpense = projectedTotalExpense - spentSoFar;

        String explanation = String.format(
                "Spent %d across %d day(s) (avg %s/day). %d / %d x %d days = %d projected for the month, %d more over %d remaining day(s).",
                spentSoFar,
                daysElapsed,
                averageDailySpend.toPlainString(),
                spentSoFar,
                daysElapsed,
                daysInMonth,
                projectedTotalExpense,
                projectedRemainingExpense,
                remainingDays
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
