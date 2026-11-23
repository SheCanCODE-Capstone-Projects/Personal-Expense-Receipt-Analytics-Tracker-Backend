package com.example.ExpenseTracker.core;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public  final class ForecastCalculator {

    public record Result(
            String month, LocalDate asOf,
            long spentSoFar, int daysElapsed, int daysInMonth, int remainingDays,
            long dailyAverage, long last7DayAverage,
            long forecast, long lowEstimate, long highEstimate,
            String formula, List<String> assumptions) {}

    private void ForecastCalculator() {}

    /** @param dailyExpenses expense total per date (only dates inside the month matter) */
    public static Result calculate(YearMonth month, LocalDate asOf, Map<LocalDate, Long> dailyExpenses) {
        int daysInMonth = month.lengthOfMonth();
        int daysElapsed;
        if (asOf.isBefore(month.atDay(1))) daysElapsed = 0;               // future month
        else if (asOf.isAfter(month.atEndOfMonth())) daysElapsed = daysInMonth; // past month
        else daysElapsed = asOf.getDayOfMonth();
        int remaining = daysInMonth - daysElapsed;


        long spent = 0;
        long last7 = 0;
        LocalDate lastCountedDay = month.atDay(1).plusDays(Math.max(daysElapsed, 1) - 1L);
        LocalDate last7Start = lastCountedDay.minusDays(6);
        for (Map.Entry<LocalDate, Long> e : dailyExpenses.entrySet()) {
            LocalDate d = e.getKey();
            boolean inElapsedPart = YearMonth.from(d).equals(month) && d.getDayOfMonth() <= daysElapsed;
            if (!inElapsedPart) continue;
            spent += e.getValue();
            if (!d.isBefore(last7Start)) last7 += e.getValue();
        }

        long dailyAvg = daysElapsed == 0 ? 0 : divideRounded(spent, daysElapsed);
        int last7Days = Math.min(7, daysElapsed);
        long last7Avg = last7Days == 0 ? 0 : divideRounded(last7, last7Days);

        long forecast = daysElapsed == 0 ? 0 : divideRounded(spent * daysInMonth, daysElapsed);
        long low = spent + Math.min(dailyAvg, last7Avg) * remaining;
        long high = spent + Math.max(dailyAvg, last7Avg) * remaining;

        String formula = daysElapsed == 0
                ? "No days of this month have passed yet, so there is nothing to project."
                : String.format("(%,d RWF spent / %d days) x %d days = %,d RWF", spent, daysElapsed, daysInMonth, forecast);

        List<String> assumptions = List.of(
                "Spending continues at the same average daily rate for the rest of the month.",
                "Only EXPENSE transactions in RWF are counted; income is ignored.",
                "Range uses the lower/higher of the whole-month and last-7-day daily averages.",
                "Large one-off purchases (e.g. rent) can make the estimate too high or too low.",
                "This is an estimate for planning, not financial advice.");

        return new Result(month.toString(), asOf, spent, daysElapsed, daysInMonth, remaining,
                dailyAvg, last7Avg, forecast, low, high, formula, assumptions);
    }
    private static long divideRounded(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round((double) numerator / denominator);
    }



}
