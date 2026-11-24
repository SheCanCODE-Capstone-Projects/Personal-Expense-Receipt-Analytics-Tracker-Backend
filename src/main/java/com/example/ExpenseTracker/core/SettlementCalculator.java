package com.example.ExpenseTracker.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class SettlementCalculator {

    private SettlementCalculator() {}

    public record Transfer(Long fromId, Long toId, long amount) {}

    private record PartyBalance(Long id, long amount) {}

    public static List<Transfer> settle(Map<Long, Long> netBalancesById) {
        if (netBalancesById == null || netBalancesById.isEmpty()) {
            return List.of();
        }

        // Max-heap for creditors (net > 0: owed money)
        PriorityQueue<PartyBalance> creditors = new PriorityQueue<>(
                Comparator.comparingLong(PartyBalance::amount).reversed());

        // Max-heap by magnitude for debtors (net < 0: owes money)
        PriorityQueue<PartyBalance> debtors = new PriorityQueue<>(
                Comparator.comparingLong(PartyBalance::amount).reversed());

        for (Map.Entry<Long, Long> entry : netBalancesById.entrySet()) {
            long net = entry.getValue() != null ? entry.getValue() : 0L;
            if (net > 0L) {
                creditors.add(new PartyBalance(entry.getKey(), net));
            } else if (net < 0L) {
                debtors.add(new PartyBalance(entry.getKey(), Math.abs(net)));
            }
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            PartyBalance creditor = creditors.poll();
            PartyBalance debtor = debtors.poll();

            long payment = Math.min(creditor.amount(), debtor.amount());
            transfers.add(new Transfer(debtor.id(), creditor.id(), payment));

            long remainingCreditor = creditor.amount() - payment;
            long remainingDebtor = debtor.amount() - payment;

            if (remainingCreditor > 0L) {
                creditors.add(new PartyBalance(creditor.id(), remainingCreditor));
            }
            if (remainingDebtor > 0L) {
                debtors.add(new PartyBalance(debtor.id(), remainingDebtor));
            }
        }

        return transfers;
    }
}