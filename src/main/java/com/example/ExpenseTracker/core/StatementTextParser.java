package com.example.ExpenseTracker.core;

import com.example.ExpenseTracker.Model.ETransactionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text extracted from a PDF statement or receipt into candidate transactions.
 * Statements: every line holding both a date and an amount becomes a row (first amount wins).
 * Receipts: when no such line exists, the TOTAL line plus any date on the page becomes one row.
 */
public final class StatementTextParser {

    private StatementTextParser() {}

    public record Candidate(int line, LocalDate date, long amount, ETransactionType type,
                            String merchant, String description) {}

    private static final Pattern DATE = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}|\\d{1,2} [A-Za-z]{3,9},? \\d{4})\\b");
    // Amounts need a decimal part or a thousands separator, so dates and reference numbers aren't mistaken for money.
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?(?![\\d,])|(?<![\\d.,])-?\\d+\\.\\d{2}(?![\\d.])");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("(?<![\\d.,])\\d+(?![\\d.,])");
    private static final Pattern TOTAL = Pattern.compile("(?i)\\b(grand total|total|amount due|amount paid)\\b");
    private static final Pattern INCOME_HINT = Pattern.compile("(?i)\\b(credit|cr|received|deposit|salary|refund)\\b");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH));

    public static List<Candidate> parse(String text) {
        String[] lines = text.split("\\R");
        List<Candidate> rows = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            LocalDate date = findDate(line);
            Matcher amount = firstAmount(line);
            if (date == null || amount == null) {
                continue;
            }
            long value = MoneyParser.parseWholeUnits(amount.group());
            if (value == 0L) {
                continue;
            }
            String description = AMOUNT.matcher(line).replaceAll("")
                    .replaceFirst(DATE.pattern(), "").replaceAll("\\s{2,}", " ").trim();
            ETransactionType type = value < 0L || !INCOME_HINT.matcher(line).find()
                    ? ETransactionType.EXPENSE : ETransactionType.INCOME;
            rows.add(new Candidate(i + 1, date, Math.abs(value), type, null, description.isEmpty() ? null : description));
        }
        return rows.isEmpty() ? parseReceipt(lines) : rows;
    }

    private static List<Candidate> parseReceipt(String[] lines) {
        LocalDate date = null;
        String merchant = null;
        Long total = null;
        int totalLine = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (merchant == null) {
                merchant = line;
            }
            if (date == null) {
                date = findDate(line);
            }
            if (!TOTAL.matcher(line).find()) {
                continue;
            }
            // A total line may be a bare whole number such as "TOTAL RWF 500".
            Matcher amount = firstAmount(line);
            if (amount == null) {
                amount = PLAIN_NUMBER.matcher(line);
                amount = amount.find() ? amount : null;
            }
            // Keep the last TOTAL line: subtotals usually come first.
            if (amount != null) {
                total = Math.abs(MoneyParser.parseWholeUnits(amount.group()));
                totalLine = i + 1;
            }
        }
        if (date == null || total == null || total == 0L) {
            return List.of();
        }
        return List.of(new Candidate(totalLine, date, total, ETransactionType.EXPENSE, merchant, "Receipt total"));
    }

    /** Parses a whole field as a date in any supported format, or returns null. */
    public static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = DATE.matcher(raw.trim());
        return m.matches() ? findDate(raw.trim()) : null;
    }

    static LocalDate findDate(String line) {
        Matcher m = DATE.matcher(line);
        while (m.find()) {
            String raw = m.group(1).replace(",", "");
            for (DateTimeFormatter f : DATE_FORMATS) {
                try {
                    return LocalDate.parse(raw, f);
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
            }
        }
        return null;
    }

    // The first amount after the date is the transaction; later ones are usually a running balance.
    private static Matcher firstAmount(String line) {
        Matcher m = AMOUNT.matcher(line);
        return m.find() ? m : null;
    }
}
