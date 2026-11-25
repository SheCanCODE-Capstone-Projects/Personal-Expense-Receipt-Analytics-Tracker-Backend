package com.example.ExpenseTracker.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SplitCalculator {

    private SplitCalculator() {}

    public record ShareInput(UUID participantId, BigDecimal value) {}

    public record SplitOutcome(String splitType, Map<UUID, Long> amountsByParticipant) {}

    public static SplitOutcome calculate(long totalAmount, List<ShareInput> participants) {
        if (totalAmount <= 0L) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }

        boolean allNullValues = participants.stream().allMatch(p -> p.value() == null);
        if (allNullValues) {
            return splitEqual(totalAmount, participants);
        }

        boolean anyNullValues = participants.stream().anyMatch(p -> p.value() == null);
        if (anyNullValues) {
            throw new IllegalArgumentException("Provide values for all participants or none for equal split");
        }

        BigDecimal sum = participants.stream()
                .map(ShareInput::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.valueOf(100)) == 0) {
            return splitPercentage(totalAmount, participants);
        }

        return splitCustom(totalAmount, participants);
    }

    private static SplitOutcome splitEqual(long totalAmount, List<ShareInput> participants) {
        int count = participants.size();
        long baseShare = totalAmount / count;
        long remainder = totalAmount % count;

        Map<UUID, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            long share = baseShare + (i < remainder ? 1L : 0L);
            result.put(participants.get(i).participantId(), share);
        }
        return new SplitOutcome("EQUAL", result);
    }

    private static SplitOutcome splitPercentage(long totalAmount, List<ShareInput> participants) {
        BigDecimal totalBd = BigDecimal.valueOf(totalAmount);
        BigDecimal hundred = BigDecimal.valueOf(100);

        Map<UUID, Long> rawShares = new LinkedHashMap<>();
        long assigned = 0L;

        for (ShareInput p : participants) {
            if (p.value().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Percentage cannot be negative");
            }
            long share = totalBd.multiply(p.value())
                    .divide(hundred, 0, RoundingMode.DOWN)
                    .longValueExact();
            rawShares.put(p.participantId(), share);
            assigned += share;
        }

        long remainder = totalAmount - assigned;
        if (remainder > 0L) {
            UUID firstId = participants.get(0).participantId();
            rawShares.put(firstId, rawShares.get(firstId) + remainder);
        }

        return new SplitOutcome("PERCENTAGE", rawShares);
    }

    private static SplitOutcome splitCustom(long totalAmount, List<ShareInput> participants) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        long sum = 0L;

        for (ShareInput p : participants) {
            long share = p.value().longValueExact();
            if (share < 0L) {
                throw new IllegalArgumentException("Custom share cannot be negative");
            }
            result.put(p.participantId(), share);
            sum += share;
        }

        if (sum != totalAmount) {
            throw new IllegalArgumentException(
                    "Custom shares (" + sum + ") must equal total amount (" + totalAmount + ")");
        }

        return new SplitOutcome("CUSTOM", result);
    }
}