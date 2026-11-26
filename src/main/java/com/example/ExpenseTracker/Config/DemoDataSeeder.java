package com.example.ExpenseTracker.Config;

import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddExpenseRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddMemberRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.CreateGroupRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.MemberResponse;
import com.example.ExpenseTracker.DTO.TransactionDTO.TransactionRequest;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ERole;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.service.BudgetService;
import com.example.ExpenseTracker.service.GroupService;
import com.example.ExpenseTracker.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Reproducible demo data (enable with the "demo" profile): two personal users with a month of
 * transactions and a budget, plus a three-person group holding a RWF 30,000 equal split.
 * Runs once; if the first demo user already exists nothing is changed.
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String AMINA = "amina@demo.local";
    public static final String JEAN = "jean@demo.local";
    public static final String ERIC = "eric@demo.local";
    /** Shared password for the demo accounts, so a live demo can use POST /api/auth/login. */
    public static final String PASSWORD = "Demo12345";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final GroupService groupService;

    public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
                          TransactionService transactionService, BudgetService budgetService,
                          GroupService groupService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.groupService = groupService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmailIgnoreCase(AMINA).isPresent()) {
            log.info("Demo data already present; skipping seed");
            return;
        }
        User amina = user(AMINA, "Amina Uwase");
        User jean = user(JEAN, "Jean Habimana");
        User eric = user(ERIC, "Eric Mugabo");

        LocalDate start = YearMonth.now().atDay(1);
        tx(amina, ETransactionType.INCOME, 450_000, start, "Salary", "Employer Ltd", "Monthly salary");
        tx(amina, ETransactionType.EXPENSE, 120_000, start, "Rent & Housing", "Landlord", "Rent");
        tx(amina, ETransactionType.EXPENSE, 18_500, start.plusDays(1), "Food & Groceries", "Simba Supermarket", "Weekly shop");
        tx(amina, ETransactionType.EXPENSE, 5_000, start.plusDays(2), "Transport", "Moto", "Commute");
        tx(amina, ETransactionType.EXPENSE, 10_000, start.plusDays(3), "Airtime & Data", "MTN", "Data bundle");
        tx(jean, ETransactionType.INCOME, 300_000, start, "Salary", "School", "Salary");
        tx(jean, ETransactionType.EXPENSE, 25_000, start.plusDays(1), "Food & Groceries", "Kimironko Market", "Groceries");

        budgetService.saveBudget(amina.getId(),
                new BudgetRequest(YearMonth.now().toString(), category("Food & Groceries", ETransactionType.EXPENSE), 60_000L, 80), "RWF");
        budgetService.saveBudget(amina.getId(),
                new BudgetRequest(YearMonth.now().toString(), null, 250_000L, 80), "RWF");

        MemberResponse owner = groupService.createGroup(amina.getId(), new CreateGroupRequest("Kigali Flatmates"));
        UUID groupId = groupService.listUserGroups(amina.getId()).getFirst().id();
        groupService.addMember(amina.getId(), groupId, new AddMemberRequest(JEAN, null));
        groupService.addMember(amina.getId(), groupId, new AddMemberRequest(ERIC, null));
        groupService.addExpense(amina.getId(), groupId,
                new AddExpenseRequest("Dinner at Repub Lounge", 30_000L, owner.id(), start.plusDays(4), List.of()), "RWF");

        log.info("Seeded demo users {}, {}, {} and group 'Kigali Flatmates'", AMINA, JEAN, ERIC);
    }

    private User user(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(ERole.MEMBER);
        user.setHashPassword(PASSWORD_HASH);
        return userRepository.save(user);
    }

    private void tx(User user, ETransactionType type, long amount, LocalDate date, String category,
                    String merchant, String note) {
        transactionService.create(user.getId(), new TransactionRequest(
                type, amount, "RWF", date, category(category, type), null, merchant, "MOBILE_MONEY", note, false));
    }

    private UUID category(String name, ETransactionType type) {
        return categoryRepository.findByUserIsNullAndNameIgnoreCaseAndType(name, type).map(Category::getId).orElseThrow();
    }
}
