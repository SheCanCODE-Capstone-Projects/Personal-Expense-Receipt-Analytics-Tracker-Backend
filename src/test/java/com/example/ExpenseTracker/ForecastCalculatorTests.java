package com.example.ExpenseTracker;

import com.example.ExpenseTracker.core.ForecastCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForecastCalculatorTests {

    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @Test
    void projectsWithTheExactDailyAverage() {
        // 10,001 over 10 days of a 30-day month: 10,001 / 10 x 30 = 30,003.
        // Rounding the average down first would give 1,000 x 20 + 10,001 = 30,001.
        var result = ForecastCalculator.calculate(NOV, LocalDate.of(2026, 11, 10), Map.of(
                LocalDate.of(2026, 11, 2), 4_000L,
                LocalDate.of(2026, 11, 9), 6_001L));

        assertEquals(10_001L, result.spentSoFar());
        assertEquals(10, result.daysElapsed());
        assertEquals(20, result.remainingDays());
        assertEquals(new BigDecimal("1000.10"), result.averageDailySpend());
        assertEquals(30_003L, result.projectedTotalExpense());
        assertEquals(20_002L, result.projectedRemainingExpense());
    }

    @Test
    void roundsTheFinalProjectionHalfUp() {
        // 1,000 / 3 x 30 = 10,000 exactly; 1,001 / 7 x 30 = 4,290.0 exactly; 100 / 7 x 30 = 428.57 -> 429.
        assertEquals(10_000L, ForecastCalculator.calculate(NOV, LocalDate.of(2026, 11, 3),
                Map.of(LocalDate.of(2026, 11, 1), 1_000L)).projectedTotalExpense());
        assertEquals(4_290L, ForecastCalculator.calculate(NOV, LocalDate.of(2026, 11, 7),
                Map.of(LocalDate.of(2026, 11, 1), 1_001L)).projectedTotalExpense());
        assertEquals(429L, ForecastCalculator.calculate(NOV, LocalDate.of(2026, 11, 7),
                Map.of(LocalDate.of(2026, 11, 1), 100L)).projectedTotalExpense());
    }

    @Test
    void ignoresSpendingAfterTheAsOfDateAndOutsideTheMonth() {
        var result = ForecastCalculator.calculate(NOV, LocalDate.of(2026, 11, 15), Map.of(
                LocalDate.of(2026, 10, 31), 99_999L,
                LocalDate.of(2026, 11, 15), 15_000L,
                LocalDate.of(2026, 11, 16), 50_000L));
        assertEquals(15_000L, result.spentSoFar());
        assertEquals(30_000L, result.projectedTotalExpense());
    }

    @Test
    void aFinishedMonthProjectsExactlyWhatWasSpent() {
        var result = ForecastCalculator.calculate(NOV, null, Map.of(LocalDate.of(2026, 11, 4), 12_345L));
        assertEquals(30, result.daysElapsed());
        assertEquals(12_345L, result.projectedTotalExpense());
        assertEquals(0L, result.projectedRemainingExpense());
    }
}
