package com.example.ExpenseTracker;

import com.example.ExpenseTracker.Config.DemoDataSeeder;
import com.example.ExpenseTracker.DTO.GroupDTO.BalanceResponse;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.service.BudgetService;
import com.example.ExpenseTracker.service.GroupService;
import com.example.ExpenseTracker.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Runs the seeder by hand inside a rolled-back transaction, so the dev database is left untouched.
@SpringBootTest
@Transactional
class DemoDataSeederTests {
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TransactionService transactions;
    @Autowired BudgetService budgets;
    @Autowired GroupService groups;

    @Test
    void seedsUsersBudgetsAndAThreeWayGroupOnce() throws Exception {
        assumeTrue(users.findByEmailIgnoreCase(DemoDataSeeder.AMINA).isEmpty(), "demo data already seeded in this database");
        var seeder = new DemoDataSeeder(users, categories, transactions, budgets, groups);
        seeder.run(null);
        seeder.run(null);

        java.util.UUID amina = users.findByEmailIgnoreCase(DemoDataSeeder.AMINA).orElseThrow().getId();
        for (String email : java.util.List.of(DemoDataSeeder.JEAN, DemoDataSeeder.ERIC, DemoDataSeeder.GRACE, DemoDataSeeder.PATRICK)) {
            assertTrue(users.findByEmailIgnoreCase(email).isPresent(), email);
        }
        assertEquals(4, budgets.listByMonth(amina, YearMonth.now().toString(), "RWF").size());
        assertTrue(transactions.list(amina, YearMonth.now().minusMonths(3).atDay(1), null, null, null, null, null, null).size() > 40,
                "four months of history");

        var aminaGroups = groups.listUserGroups(amina);
        assertEquals(2, aminaGroups.size());
        var flatmates = aminaGroups.stream().filter(g -> g.name().equals(DemoDataSeeder.FLATMATES)).findFirst().orElseThrow();
        assertEquals(java.util.List.of(20_000L, -10_000L, -10_000L),
                groups.getBalances(amina, flatmates.id(), "RWF").stream().map(BalanceResponse::net).toList());

        // Every group's balances still add up to zero after percentage, exact and equal splits and a settlement.
        var trip = aminaGroups.stream().filter(g -> g.name().equals(DemoDataSeeder.TRIP)).findFirst().orElseThrow();
        assertEquals(0L, groups.getBalances(amina, trip.id(), "RWF").stream().mapToLong(BalanceResponse::net).sum());
        assertEquals(1, groups.listSettlements(amina, trip.id()).size());
    }
}
