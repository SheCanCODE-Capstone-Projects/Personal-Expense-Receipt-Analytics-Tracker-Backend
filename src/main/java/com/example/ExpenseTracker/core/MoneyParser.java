package com.example.ExpenseTracker.core;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyParser {
    private MoneyParser() {}

        public static long parseToMinorUnits(String rawAmount, int scale) {
            if (rawAmount == null || rawAmount.isBlank()) {
                throw new IllegalArgumentException("Amount is required");
            }
            String cleaned = rawAmount.trim()
                    .replaceAll("[A-Za-z$€£¥]", "")
                    .replace(",", "")
                    .trim();
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("Invalid amount: " + rawAmount);
            }

            BigDecimal bd;
            try {
                bd = new BigDecimal(cleaned);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric amount: " + rawAmount);
            }

            if (scale < 0) {
                throw new IllegalArgumentException("Currency scale cannot be negative");
            }

            BigDecimal multiplier = BigDecimal.TEN.pow(scale);
            return bd.multiply(multiplier)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        }

        /**
         * Convenience parser for whole-unit currencies like RWF (scale = 0).
         */
        public static long parseWholeUnits(String rawAmount) {
            return parseToMinorUnits(rawAmount, 0);
        }
    }
;
