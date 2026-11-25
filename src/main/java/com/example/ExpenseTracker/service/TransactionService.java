package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.TransactionDTO.GenerateRecurringResult;
import com.example.ExpenseTracker.DTO.TransactionDTO.TransactionRequest;
import com.example.ExpenseTracker.DTO.TransactionDTO.TransactionResponse;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Account;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.EAccount;
import com.example.ExpenseTracker.Model.EMethod;
import com.example.ExpenseTracker.Model.EReccuringFrequency;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.AccountRepository;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.TransactionRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    public TransactionResponse create(UUID userId, TransactionRequest req) {
        User user = getUserOr404(userId);
        Account account = resolveAccount(userId, req.accountId());
        Category category = resolveCategory(userId, req.categoryId(), req.type());

        LocalDateTime txDateTime = toStartOfDayOrNow(req.date());
        String currency = resolveCurrency(req.currency(), account.getCurrency());
        EMethod paymentMethod = parsePaymentMethod(req.paymentMethod());
        EReccuringFrequency frequency = req.isRecurring() ? EReccuringFrequency.MONTHLY : null;

        String fingerprint = buildFingerprint(
                userId, txDateTime, req.amount(), currency, req.type(), req.merchant(), req.note());
        boolean possibleDuplicate = transactionRepository.existsByUserIdAndFingerprint(userId, fingerprint);

        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAccount(account);
        tx.setCategory(category);
        tx.setAmount(req.amount());
        tx.setTransactionType(req.type());
        tx.setCurrency(currency);
        tx.setTransactionDate(txDateTime);
        tx.setMerchant(req.merchant());
        tx.setPaymentMethod(paymentMethod);
        tx.setDescription(req.note());
        tx.setRecurring(req.isRecurring());
        tx.setRecurrenceFrequency(frequency);
        tx.setFingerprint(fingerprint);

        Transaction saved = transactionRepository.save(tx);
        return toResponse(saved, "MANUAL", possibleDuplicate);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID userId, UUID transactionId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return toResponse(tx, resolveSource(tx), false);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(
            UUID userId,
            LocalDate from,
            LocalDate to,
            ETransactionType type,
            String currency,
            UUID categoryId,
            UUID accountId,
            Boolean recurring) {

        LocalDateTime start = (from != null ? from : LocalDate.now().withDayOfMonth(1)).atStartOfDay();
        LocalDateTime end = (to != null ? to : LocalDate.now()).atTime(LocalTime.MAX);

        List<Transaction> rows;
        if (Boolean.TRUE.equals(recurring)) {
            rows = transactionRepository.findByUserIdAndRecurringTrueAndTransactionDateBetween(userId, start, end);
        } else if (type != null && currency != null && !currency.isBlank()) {
            rows = transactionRepository.findByUserIdAndTransactionTypeAndCurrencyAndTransactionDateBetween(
                    userId, type, currency.trim().toUpperCase(), start, end);
        } else {
            rows = transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                    userId, start, end);
        }

        return rows.stream()
                .filter(tx -> type == null || tx.getTransactionType() == type)
                .filter(tx -> currency == null || currency.isBlank() || tx.getCurrency().equalsIgnoreCase(currency.trim()))
                .filter(tx -> categoryId == null || (tx.getCategory() != null && tx.getCategory().getId().equals(categoryId)))
                .filter(tx -> accountId == null || (tx.getAccount() != null && tx.getAccount().getId().equals(accountId)))
                .filter(tx -> recurring == null || tx.isRecurring() == recurring)
                .map(tx -> toResponse(tx, resolveSource(tx), false))
                .toList();
    }

    public TransactionResponse update(UUID userId, UUID transactionId, TransactionRequest req) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        Account account = resolveAccount(userId, req.accountId() != null ? req.accountId() : tx.getAccount().getId());
        Category category = req.categoryId() == null && tx.getCategory().getType() == req.type()
                ? tx.getCategory()
                : resolveCategory(userId, req.categoryId(), req.type());
        LocalDateTime txDateTime = req.date() != null ? req.date().atStartOfDay() : tx.getTransactionDate();
        String currency = resolveCurrency(req.currency(), account.getCurrency());

        tx.setAccount(account);
        tx.setCategory(category);
        tx.setAmount(req.amount());
        tx.setTransactionType(req.type());
        tx.setCurrency(currency);
        tx.setTransactionDate(txDateTime);
        tx.setMerchant(req.merchant());
        tx.setPaymentMethod(parsePaymentMethod(req.paymentMethod()));
        tx.setDescription(req.note());
        tx.setRecurring(req.isRecurring());
        tx.setRecurrenceFrequency(req.isRecurring()
                ? (tx.getRecurrenceFrequency() != null ? tx.getRecurrenceFrequency() : EReccuringFrequency.MONTHLY)
                : null);
        tx.setFingerprint(buildFingerprint(
                userId, txDateTime, req.amount(), currency, req.type(), req.merchant(), req.note()));

        Transaction saved = transactionRepository.save(tx);
        return toResponse(saved, resolveSource(saved), false);
    }

    public void delete(UUID userId, UUID transactionId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        transactionRepository.delete(tx);
    }

    public GenerateRecurringResult generateRecurring(UUID userId, LocalDate upToDate) {
        User user = getUserOr404(userId);
        LocalDate targetEnd = upToDate != null ? upToDate : LocalDate.now().withDayOfMonth(
                LocalDate.now().lengthOfMonth());

        LocalDateTime windowStart = LocalDate.now().minusYears(1).atStartOfDay();
        LocalDateTime windowEnd = LocalDate.now().atTime(LocalTime.MAX);

        List<Transaction> templates = transactionRepository
                .findByUserIdAndRecurringTrueAndTransactionDateBetween(userId, windowStart, windowEnd);

        int created = 0;
        int skipped = 0;

        for (Transaction template : templates) {
            EReccuringFrequency freq = template.getRecurrenceFrequency() != null
                    ? template.getRecurrenceFrequency()
                    : EReccuringFrequency.MONTHLY;

            LocalDate nextDate = advanceDate(template.getTransactionDate().toLocalDate(), freq);
            while (!nextDate.isAfter(targetEnd)) {
                LocalDateTime occurrenceDt = nextDate.atTime(template.getTransactionDate().toLocalTime());
                String fp = buildFingerprint(
                        userId,
                        occurrenceDt,
                        template.getAmount(),
                        template.getCurrency(),
                        template.getTransactionType(),
                        template.getMerchant(),
                        template.getDescription());

                if (transactionRepository.existsByUserIdAndFingerprint(userId, fp)) {
                    skipped++;
                } else {
                    Transaction clone = new Transaction();
                    clone.setUser(user);
                    clone.setAccount(template.getAccount());
                    clone.setCategory(template.getCategory());
                    clone.setAmount(template.getAmount());
                    clone.setTransactionType(template.getTransactionType());
                    clone.setCurrency(template.getCurrency());
                    clone.setTransactionDate(occurrenceDt);
                    clone.setMerchant(template.getMerchant());
                    clone.setPaymentMethod(template.getPaymentMethod());
                    clone.setDescription(template.getDescription());
                    clone.setRecurring(false);
                    clone.setFingerprint(fp);
                    transactionRepository.save(clone);
                    created++;
                }
                nextDate = advanceDate(nextDate, freq);
            }
        }

        return new GenerateRecurringResult(created, skipped);
    }

    private User getUserOr404(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Account resolveAccount(UUID userId, UUID accountId) {
        if (accountId != null) {
            return accountRepository.findByIdAndUser_Id(accountId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        }
        List<Account> userAccounts = accountRepository.findByUser_Id(userId);
        if (!userAccounts.isEmpty()) {
            return userAccounts.get(0);
        }
        User user = getUserOr404(userId);
        Account defaultAccount = new Account();
        defaultAccount.setUser(user);
        defaultAccount.setAccountName("Main Wallet");
        defaultAccount.setAccountType(EAccount.MOBILE_MONEY);
        defaultAccount.setCurrency("RWF");
        return accountRepository.save(defaultAccount);
    }

    private Category resolveCategory(UUID userId, UUID categoryId, ETransactionType type) {
        if (categoryId == null) {
            return categoryService.fallback(type);
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Category not found");
        }
        if (category.getType() != type) {
            throw new IllegalArgumentException("Category '" + category.getName() + "' is for " + category.getType() + " transactions");
        }
        return category;
    }

    private String resolveCurrency(String requestedCurrency, String fallbackCurrency) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            return requestedCurrency.trim().toUpperCase();
        }
        if (fallbackCurrency != null && !fallbackCurrency.isBlank()) {
            return fallbackCurrency.trim().toUpperCase();
        }
        return "RWF";
    }

    private LocalDateTime toStartOfDayOrNow(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.now();
    }

    private EMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        try {
            return EMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment method: " + method);
        }
    }

    private LocalDate advanceDate(LocalDate current, EReccuringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }

    private String resolveSource(Transaction tx) {
        return tx.getImportBatch() != null ? "IMPORT" : "MANUAL";
    }

    private String buildFingerprint(
            UUID userId,
            LocalDateTime dateTime,
            Long amount,
            String currency,
            ETransactionType type,
            String merchant,
            String note) {
        String raw = String.join("|",
                String.valueOf(userId),
                dateTime.toLocalDate().toString(),
                String.valueOf(amount),
                currency != null ? currency : "RWF",
                type != null ? type.name() : "",
                merchant != null ? merchant.trim().toLowerCase() : "",
                note != null ? note.trim().toLowerCase() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private TransactionResponse toResponse(Transaction tx, String source, boolean possibleDuplicate) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionDate().toLocalDate(),
                tx.getCategory() != null ? tx.getCategory().getId() : null,
                tx.getCategory() != null ? tx.getCategory().getName() : null,
                tx.getAccount() != null ? tx.getAccount().getId() : null,
                tx.getMerchant(),
                tx.getPaymentMethod() != null ? tx.getPaymentMethod().name() : null,
                tx.getDescription(),
                tx.isRecurring(),
                source,
                possibleDuplicate);
    }
}