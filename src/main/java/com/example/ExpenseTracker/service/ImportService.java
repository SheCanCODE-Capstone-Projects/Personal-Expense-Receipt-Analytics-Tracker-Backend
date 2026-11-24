package com.example.ExpenseTracker.service;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Account;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.EAccount;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.AccountRepository;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.TransactionRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.core.MoneyParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
@Service
@Transactional
public class ImportService {



    public record ParsedRow(
                int rowNumber,
                LocalDate date,
                long amount,
                String currency,
                ETransactionType type,
                String merchant,
                String description,
                String fingerprint,
                boolean duplicate,
                String error
        ) {}

        public record ImportPreviewResponse(
                String currency,
                Long accountId,
                Long categoryId,
                int totalRows,
                int validRows,
                int duplicateRows,
                int invalidRows,
                List<ParsedRow> rows
        ) {}

        public record ConfirmImportRequest(
                Long accountId,
                Long categoryId,
                String currency,
                boolean skipDuplicates,
                List<ImportRowInput> rows
        ) {}

        public record ImportRowInput(
                LocalDate date,
                long amount,
                ETransactionType type,
                String merchant,
                String description
        ) {}

        public record ImportResultResponse(
                int importedCount,
                int skippedDuplicates,
                List<Long> createdTransactionIds
        ) {}

        private final TransactionRepository transactionRepository;
        private final AccountRepository accountRepository;
        private final CategoryRepository categoryRepository;
        private final UserRepository userRepository;

        public ImportService(
                TransactionRepository transactionRepository,
                AccountRepository accountRepository,
                CategoryRepository categoryRepository,
                UserRepository userRepository) {
            this.transactionRepository = transactionRepository;
            this.accountRepository = accountRepository;
            this.categoryRepository = categoryRepository;
            this.userRepository = userRepository;
        }

        @Transactional(readOnly = true)
        public ImportPreviewResponse previewCsv(
                Long userId,
                String csvContent,
                String currencyParam,
                Long accountId,
                Long categoryId) {

            getUserOr404(userId);
            Account account = resolveAccount(userId, accountId);
            Category category = resolveCategory(userId, categoryId);
            String currency = resolveCurrency(currencyParam, account.getCurrency());

            if (csvContent == null || csvContent.isBlank()) {
                throw new IllegalArgumentException("CSV content is empty");
            }

            String[] lines = csvContent.split("\\R");
            List<ParsedRow> parsedRows = new ArrayList<>();
            int rowNumber = 0;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (i == 0 && looksLikeHeader(line)) {
                    continue;
                }
                rowNumber++;
                parsedRows.add(parseLine(rowNumber, line, userId, currency));
            }

            int duplicateCount = (int) parsedRows.stream().filter(r -> r.error() == null && r.duplicate()).count();
            int invalidCount = (int) parsedRows.stream().filter(r -> r.error() != null).count();
            int validCount = parsedRows.size() - invalidCount;

            return new ImportPreviewResponse(
                    currency,
                    account.getId(),
                    category != null ? category.getId() : null,
                    parsedRows.size(),
                    validCount,
                    duplicateCount,
                    invalidCount,
                    parsedRows
            );
        }

        public ImportResultResponse confirmImport(Long userId, ConfirmImportRequest request) {
            User user = getUserOr404(userId);
            Account account = resolveAccount(userId, request.accountId());
            Category category = resolveCategory(userId, request.categoryId());
            String currency = resolveCurrency(request.currency(), account.getCurrency());

            if (request.rows() == null || request.rows().isEmpty()) {
                throw new IllegalArgumentException("No rows provided to import");
            }

            int importedCount = 0;
            int skippedDuplicates = 0;
            List<Long> createdIds = new ArrayList<>();

            for (ImportRowInput row : request.rows()) {
                if (row.date() == null) {
                    throw new IllegalArgumentException("Row date is required");
                }
                if (row.amount() <= 0L) {
                    throw new IllegalArgumentException("Row amount must be greater than zero");
                }
                ETransactionType type = row.type() != null ? row.type() : ETransactionType.EXPENSE;
                LocalDateTime txDateTime = row.date().atStartOfDay();

                String fingerprint = buildFingerprint(
                        userId,
                        txDateTime,
                        row.amount(),
                        currency,
                        type,
                        row.merchant(),
                        row.description()
                );

                boolean exists = transactionRepository.existsByUserIdAndFingerprint(userId, fingerprint);
                if (exists && request.skipDuplicates()) {
                    skippedDuplicates++;
                    continue;
                }

                Transaction tx = new Transaction();
                tx.setUser(user);
                tx.setAccount(account);
                tx.setCategory(category);
                tx.setAmount(row.amount());
                tx.setCurrency(currency);
                tx.setTransactionType(type);
                tx.setTransactionDate(txDateTime);
                tx.setMerchant(row.merchant());
                tx.setDescription(row.description());
                tx.setRecurring(false);
                tx.setFingerprint(fingerprint);

                Transaction saved = transactionRepository.save(tx);
                createdIds.add(saved.getId());
                importedCount++;
            }

            return new ImportResultResponse(importedCount, skippedDuplicates, createdIds);
        }

        private ParsedRow parseLine(int rowNumber, String line, Long userId, String currency) {
            // Expected columns: date,amount,type,merchant,description (type, merchant, description optional)
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
            }

            if (parts.length < 2) {
                return new ParsedRow(
                        rowNumber, null, 0L, currency, null, null, line,
                        null, false, "Expected at least date and amount columns"
                );
            }

            LocalDate date;
            try {
                date = LocalDate.parse(parts[0]);
            } catch (DateTimeParseException ex) {
                return new ParsedRow(
                        rowNumber, null, 0L, currency, null, null, line,
                        null, false, "Invalid date '" + parts[0] + "' (use YYYY-MM-DD)"
                );
            }

            long amount;
            try {
                amount = MoneyParser.parseWholeUnits(parts[1]);
                if (amount <= 0L) {
                    throw new IllegalArgumentException("Amount must be greater than zero");
                }
            } catch (Exception ex) {
                return new ParsedRow(
                        rowNumber, date, 0L, currency, null, null, line,
                        null, false, "Invalid amount '" + parts[1] + "'"
                );
            }

            ETransactionType type = ETransactionType.EXPENSE;
            if (parts.length >= 3 && !parts[2].isBlank()) {
                try {
                    type = ETransactionType.valueOf(parts[2].trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    return new ParsedRow(
                            rowNumber, date, amount, currency, null, null, line,
                            null, false, "Invalid transaction type '" + parts[2] + "'"
                    );
                }
            }

            String merchant = (parts.length >= 4 && !parts[3].isBlank()) ? parts[3].trim() : null;
            String description = (parts.length >= 5 && !parts[4].isBlank()) ? parts[4].trim() : null;

            String fingerprint = buildFingerprint(
                    userId, date.atStartOfDay(), amount, currency, type, merchant, description);
            boolean duplicate = transactionRepository.existsByUserIdAndFingerprint(userId, fingerprint);

            return new ParsedRow(
                    rowNumber, date, amount, currency, type, merchant, description,
                    fingerprint, duplicate, null
            );
        }

        private boolean looksLikeHeader(String firstLine) {
            String lower = firstLine.toLowerCase();
            return lower.contains("date") && lower.contains("amount");
        }

        private User getUserOr404(Long userId) {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        private Account resolveAccount(Long userId, Long accountId) {
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

        private Category resolveCategory(Long userId, Long categoryId) {
            if (categoryId == null) {
                return null;
            }
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
                throw new ResourceNotFoundException("Category not found");
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

        private String buildFingerprint(
                Long userId,
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
    }
;
