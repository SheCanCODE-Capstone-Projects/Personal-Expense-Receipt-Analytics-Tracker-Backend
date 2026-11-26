package com.example.ExpenseTracker.Config;

import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddExpenseRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddMemberRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.CreateGroupRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.GroupResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.MemberResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.Participant;
import com.example.ExpenseTracker.DTO.GroupDTO.SettlementRequest;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.example.ExpenseTracker.Model.ETransactionType.EXPENSE;
import static com.example.ExpenseTracker.Model.ETransactionType.INCOME;

/**
 * Reproducible demo data (enable with APP_DEMO_SEED=true or the "demo" profile).
 *
 * <ul>
 *   <li>Five users who all sign in with {@link #PASSWORD}.</li>
 *   <li>Four months of personal transactions for Amina, Jean and Grace, ending today.</li>
 *   <li>Budgets for this month and last month: some OK, some near the limit, one exceeded.</li>
 *   <li>Three groups: <b>Kigali Flatmates</b> (RWF 30,000 dinner split equally among three),
 *       <b>Musanze Weekend Trip</b> (percentage, exact-amount and equal splits plus a settlement)
 *       and <b>Office Lunch Club</b> (owned by Jean).</li>
 * </ul>
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
    public static final String GRACE = "grace@demo.local";
    public static final String PATRICK = "patrick@demo.local";
    /** Shared password for the demo accounts, so a live demo can use POST /api/auth/login. */
    public static final String PASSWORD = "Demo12345";
    public static final String FLATMATES = "Kigali Flatmates";
    public static final String TRIP = "Musanze Weekend Trip";
    public static final String LUNCH = "Office Lunch Club";
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
        User amina = user(AMINA, "Amina Uwase", ERole.OWNER);
        User jean = user(JEAN, "Jean Habimana", ERole.TREASURER);
        User eric = user(ERIC, "Eric Mugabo", ERole.MEMBER);
        User grace = user(GRACE, "Grace Ingabire", ERole.MEMBER);
        User patrick = user(PATRICK, "Patrick Niyonzima", ERole.Guest);

        YearMonth now = YearMonth.now();
        for (int back = 3; back >= 0; back--) {
            YearMonth month = now.minusMonths(back);
            aminaMonth(amina, month, back);
            jeanMonth(jean, month, back);
            graceMonth(grace, month, back);
        }
        // A few one-off records on the other accounts so every user has a non-empty dashboard.
        tx(eric, INCOME, 180_000, day(now, 1), "Business", "Freelance design", "Logo project", "CARD", false);
        tx(eric, EXPENSE, 15_000, day(now, 3), "Transport", "Volcano Express", "Bus to Huye", "CASH", false);
        tx(patrick, EXPENSE, 7_500, day(now, 2), "Food & Groceries", "Java House", "Coffee and snacks", "CARD", false);

        budgets(amina, jean, grace, now);
        groups(amina, jean, eric, grace, patrick, now);

        log.info("Seeded demo users {}, {}, {}, {}, {} (password {}) and groups '{}', '{}', '{}'",
                AMINA, JEAN, ERIC, GRACE, PATRICK, PASSWORD, FLATMATES, TRIP, LUNCH);
    }

    // ---- Personal ledgers ----

    private void aminaMonth(User u, YearMonth m, int monthsAgo) {
        boolean current = monthsAgo == 0;
        tx(u, INCOME, 450_000, day(m, 1), "Salary", "Employer Ltd", "Monthly salary", "MOBILE_MONEY", current);
        tx(u, EXPENSE, 120_000, day(m, 1), "Rent & Housing", "Landlord", "Rent", "MOBILE_MONEY", current);
        tx(u, EXPENSE, 18_500 + monthsAgo * 1_200, day(m, 2), "Food & Groceries", "Simba Supermarket", "Weekly shop", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 16_200, day(m, 9), "Food & Groceries", "Kimironko Market", "Vegetables and fruit", "CASH", false);
        tx(u, EXPENSE, 21_300, day(m, 16), "Food & Groceries", "Simba Supermarket", "Weekly shop", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 14_800, day(m, 23), "Food & Groceries", "Sawa Citi", "Weekly shop", "CARD", false);
        tx(u, EXPENSE, 5_000, day(m, 3), "Transport", "Moto", "Commute", "CASH", false);
        tx(u, EXPENSE, 12_000, day(m, 12), "Transport", "Yego Cabs", "Airport drop-off", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 10_000, day(m, 4), "Airtime & Data", "MTN", "Data bundle", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 14_500, day(m, 6), "Utilities", "REG / Cash Power", "Electricity tokens", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 6_000, day(m, 6), "Utilities", "WASAC", "Water bill", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 9_900, day(m, 10), "Entertainment", "Canal+ Rwanda", "TV subscription", "CARD", current);
        if (monthsAgo % 2 == 0) {
            tx(u, EXPENSE, 35_000, day(m, 18), "Shopping", "Kigali Heights", "Shoes", "CARD", false);
        }
        if (monthsAgo == 1) {
            tx(u, EXPENSE, 25_000, day(m, 20), "Health", "King Faisal Hospital", "Check-up", "CARD", false);
            tx(u, INCOME, 60_000, day(m, 25), "Gifts", "Family", "Birthday gift", "MOBILE_MONEY", false);
        }
        if (current) {
            // Pushes this month's food budget past its warning level.
            tx(u, EXPENSE, 12_500, day(m, 21), "Food & Groceries", "Heaven Restaurant", "Dinner out", "CARD", false);
        }
    }

    private void jeanMonth(User u, YearMonth m, int monthsAgo) {
        tx(u, INCOME, 300_000, day(m, 1), "Salary", "Green Hills Academy", "Salary", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 80_000, day(m, 2), "Rent & Housing", "Landlord", "Rent", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 25_000, day(m, 3), "Food & Groceries", "Kimironko Market", "Groceries", "CASH", false);
        tx(u, EXPENSE, 22_000, day(m, 17), "Food & Groceries", "Kimironko Market", "Groceries", "CASH", false);
        tx(u, EXPENSE, 30_000, day(m, 5), "Education", "University of Rwanda", "Evening course fees", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 8_000, day(m, 8), "Transport", "Moto", "Commute", "CASH", false);
        tx(u, EXPENSE, 5_000, day(m, 9), "Airtime & Data", "Airtel", "Airtime", "MOBILE_MONEY", false);
        if (monthsAgo == 2) {
            tx(u, INCOME, 75_000, day(m, 20), "Business", "Private tutoring", "Weekend classes", "MOBILE_MONEY", false);
        }
    }

    private void graceMonth(User u, YearMonth m, int monthsAgo) {
        tx(u, INCOME, 220_000, day(m, 1), "Business", "Grace Tailoring", "Shop sales", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 60_000, day(m, 2), "Rent & Housing", "Landlord", "Shop and room rent", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 45_000, day(m, 7), "Shopping", "Nyabugogo Market", "Fabric stock", "CASH", false);
        tx(u, EXPENSE, 19_000, day(m, 11), "Food & Groceries", "Simba Supermarket", "Groceries", "MOBILE_MONEY", false);
        tx(u, EXPENSE, 4_000, day(m, 14), "Airtime & Data", "MTN", "Data bundle", "MOBILE_MONEY", false);
        if (monthsAgo == 0) {
            tx(u, EXPENSE, 15_000, day(m, 19), "Health", "Pharmacie Conseil", "Medicine", "CASH", false);
        }
    }

    private void budgets(User amina, User jean, User grace, YearMonth now) {
        String thisMonth = now.toString();
        String lastMonth = now.minusMonths(1).toString();
        budget(amina, thisMonth, "Food & Groceries", 60_000, 80);   // above 80%: WARNING or EXCEEDED
        budget(amina, thisMonth, null, 250_000, 80);                 // overall
        budget(amina, thisMonth, "Transport", 25_000, 80);
        budget(amina, thisMonth, "Entertainment", 15_000, 90);
        budget(amina, lastMonth, "Food & Groceries", 60_000, 80);
        budget(amina, lastMonth, null, 250_000, 80);
        budget(jean, thisMonth, "Food & Groceries", 40_000, 75);     // EXCEEDED
        budget(jean, thisMonth, null, 200_000, 80);
        budget(grace, thisMonth, "Shopping", 60_000, 80);
    }

    // ---- Shared expenses ----

    private void groups(User amina, User jean, User eric, User grace, User patrick, YearMonth now) {
        // 1. Kigali Flatmates: RWF 30,000 dinner paid by Amina, split equally → +20,000 / −10,000 / −10,000.
        MemberResponse aminaFlat = groupService.createGroup(amina.getId(), new CreateGroupRequest(FLATMATES));
        UUID flat = groupId(amina, FLATMATES);
        groupService.addMember(amina.getId(), flat, new AddMemberRequest(JEAN, null));
        groupService.addMember(amina.getId(), flat, new AddMemberRequest(ERIC, null));
        groupService.addExpense(amina.getId(), flat,
                new AddExpenseRequest("Dinner at Repub Lounge", 30_000L, aminaFlat.id(), day(now, 5), List.of()), "RWF");

        // 2. Musanze Weekend Trip: every split type, and one settlement already paid.
        MemberResponse aminaTrip = groupService.createGroup(amina.getId(), new CreateGroupRequest(TRIP));
        UUID trip = groupId(amina, TRIP);
        MemberResponse graceTrip = groupService.addMember(amina.getId(), trip, new AddMemberRequest(GRACE, "Grace"));
        MemberResponse patrickTrip = groupService.addMember(amina.getId(), trip, new AddMemberRequest(PATRICK, "Patrick"));
        MemberResponse jeanTrip = groupService.addMember(amina.getId(), trip, new AddMemberRequest(JEAN, "Jean"));
        LocalDate tripDay = day(now.minusMonths(1), 14);
        groupService.addExpense(amina.getId(), trip, new AddExpenseRequest("Car hire and fuel", 60_000L, aminaTrip.id(), tripDay,
                List.of(new Participant(aminaTrip.id(), pct(40)), new Participant(graceTrip.id(), pct(20)),
                        new Participant(patrickTrip.id(), pct(20)), new Participant(jeanTrip.id(), pct(20)))), "RWF");
        groupService.addExpense(amina.getId(), trip, new AddExpenseRequest("Lodge, two nights", 96_000L, graceTrip.id(), tripDay,
                List.of(new Participant(aminaTrip.id(), amount(30_000)), new Participant(graceTrip.id(), amount(30_000)),
                        new Participant(patrickTrip.id(), amount(18_000)), new Participant(jeanTrip.id(), amount(18_000)))), "RWF");
        groupService.addExpense(amina.getId(), trip, new AddExpenseRequest("Gorilla trek lunch", 24_000L, patrickTrip.id(),
                tripDay.plusDays(1), List.of()), "RWF");
        groupService.recordSettlement(amina.getId(), trip,
                new SettlementRequest(jeanTrip.id(), graceTrip.id(), 12_000L, "RWF"));

        // 3. Office Lunch Club, owned by Jean.
        MemberResponse jeanLunch = groupService.createGroup(jean.getId(), new CreateGroupRequest(LUNCH));
        UUID lunch = groupId(jean, LUNCH);
        MemberResponse ericLunch = groupService.addMember(jean.getId(), lunch, new AddMemberRequest(ERIC, "Eric"));
        MemberResponse graceLunch = groupService.addMember(jean.getId(), lunch, new AddMemberRequest(GRACE, "Grace"));
        groupService.addExpense(jean.getId(), lunch,
                new AddExpenseRequest("Brochettes Friday", 18_000L, jeanLunch.id(), day(now, 7), List.of()), "RWF");
        groupService.addExpense(jean.getId(), lunch,
                new AddExpenseRequest("Pizza for the team", 27_000L, ericLunch.id(), day(now, 14), List.of()), "RWF");
        groupService.addExpense(jean.getId(), lunch,
                new AddExpenseRequest("Coffee round", 6_000L, graceLunch.id(), day(now, 15),
                        List.of(new Participant(jeanLunch.id(), null), new Participant(graceLunch.id(), null))), "RWF");
    }

    // ---- Helpers ----

    private User user(String email, String name, ERole role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setHashPassword(PASSWORD_HASH);
        return userRepository.save(user);
    }

    private void tx(User user, ETransactionType type, long amount, LocalDate date, String category,
                    String merchant, String note, String paymentMethod, boolean recurring) {
        transactionService.create(user.getId(), new TransactionRequest(
                type, amount, "RWF", date, category(category, type), null, merchant, paymentMethod, note, recurring));
    }

    private void budget(User user, String month, String category, long limit, int warnPercent) {
        budgetService.saveBudget(user.getId(),
                new BudgetRequest(month, category == null ? null : category(category, EXPENSE), limit, warnPercent), "RWF");
    }

    private UUID groupId(User user, String name) {
        return groupService.listUserGroups(user.getId()).stream()
                .filter(g -> g.name().equals(name)).map(GroupResponse::id).findFirst().orElseThrow();
    }

    /** The given day of the month, but never in the future, so the current month only shows what has happened. */
    private static LocalDate day(YearMonth month, int dayOfMonth) {
        LocalDate date = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        LocalDate today = LocalDate.now();
        return date.isAfter(today) ? today : date;
    }

    private static BigDecimal pct(int percent) {
        return BigDecimal.valueOf(percent);
    }

    private static BigDecimal amount(long rwf) {
        return BigDecimal.valueOf(rwf);
    }

    private UUID category(String name, ETransactionType type) {
        return categoryRepository.findByUserIsNullAndNameIgnoreCaseAndType(name, type).map(Category::getId).orElseThrow();
    }
}
