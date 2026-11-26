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
        assertTrue(users.findByEmailIgnoreCase(DemoDataSeeder.JEAN).isPresent());
        assertEquals(2, budgets.listByMonth(amina, YearMonth.now().toString(), "RWF").size());
        var group = groups.listUserGroups(amina);
        assertEquals(1, group.size());
        assertEquals(java.util.List.of(20_000L, -10_000L, -10_000L),
                groups.getBalances(amina, group.getFirst().id(), "RWF").stream().map(BalanceResponse::net).toList());
    }
}
