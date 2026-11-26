package com.example.ExpenseTracker.service;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.Account;
import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.EAccount;
import com.example.ExpenseTracker.Model.EImportStatus;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.ImportBatch;
import com.example.ExpenseTracker.Model.Transaction;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.AccountRepository;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.Repository.ImportBatchRepository;
import com.example.ExpenseTracker.Repository.TransactionRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.core.MoneyParser;
import com.example.ExpenseTracker.core.StatementTextParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
                String duplicateReason,
                String error
        ) {}

        public record ImportPreviewResponse(
                String fileType,
                String currency,
                UUID accountId,
                UUID categoryId,
                int totalRows,
                int validRows,
                int duplicateRows,
                int invalidRows,
                List<ParsedRow> rows
        ) {}

        public record ConfirmImportRequest(
                UUID accountId,
                UUID categoryId,
                String currency,
                String fileName,
                // Null means true: re-importing the same statement must not create duplicates.
                Boolean skipDuplicates,
                List<ImportRowInput> rows
        ) {}

        public record ImportRowInput(
                LocalDate date,
                long amount,
                ETransactionType type,
                String merchant,
                String description,
                UUID categoryId,
                Boolean allowDuplicate
        ) {}

        public record ImportResultResponse(
                UUID batchId,
                int importedCount,
                int skippedDuplicates,
                List<UUID> createdTransactionIds
        ) {}

        private record RowDraft(int rowNumber, LocalDate date, long amount, ETransactionType type,
                                String merchant, String description, String error, String raw) {}

        private final TransactionRepository transactionRepository;
        private final AccountRepository accountRepository;
        private final CategoryRepository categoryRepository;
        private final UserRepository userRepository;
        private final ImportBatchRepository importBatchRepository;
        private final CategoryService categoryService;

        public ImportService(
                TransactionRepository transactionRepository,
                AccountRepository accountRepository,
                CategoryRepository categoryRepository,
                UserRepository userRepository,
                ImportBatchRepository importBatchRepository,
                CategoryService categoryService) {
            this.transactionRepository = transactionRepository;
            this.accountRepository = accountRepository;
            this.categoryRepository = categoryRepository;
            this.userRepository = userRepository;
            this.importBatchRepository = importBatchRepository;
            this.categoryService = categoryService;
        }

        public ImportPreviewResponse previewCsv(
                UUID userId,
                String csvContent,
                String currencyParam,
                UUID accountId,
                UUID categoryId) {
            if (csvContent == null || csvContent.isBlank()) {
                throw new IllegalArgumentException("CSV content is empty");
            }

            String[] lines = csvContent.split("\\R");
            List<RowDraft> drafts = new ArrayList<>();
            int rowNumber = 0;
            boolean seenContent = false;
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!seenContent) {
                    seenContent = true;
                    if (looksLikeHeader(line)) {
                        continue;
                    }
                }
                rowNumber++;
                drafts.add(parseLine(rowNumber, line));
            }
            return buildPreview(userId, "CSV", drafts, currencyParam, accountId, categoryId);
        }

        public ImportPreviewResponse previewPdfText(
                UUID userId,
                String text,
                String currencyParam,
                UUID accountId,
                UUID categoryId) {
            List<RowDraft> drafts = StatementTextParser.parse(text).stream()
                    .map(c -> new RowDraft(c.line(), c.date(), c.amount(), c.type(), c.merchant(), c.description(), null, null))
                    .toList();
            if (drafts.isEmpty()) {
                throw new IllegalArgumentException(
                        "No transactions found in the PDF. Each statement line needs a date and an amount, "
                                + "or a receipt needs a date and a TOTAL line.");
            }
            return buildPreview(userId, "PDF", drafts, currencyParam, accountId, categoryId);
        }

        private ImportPreviewResponse buildPreview(UUID userId, String fileType, List<RowDraft> drafts,
                                                   String currencyParam, UUID accountId, UUID categoryId) {
            getUserOr404(userId);
            Account account = resolveAccount(userId, accountId);
            Category category = categoryId != null ? resolveCategory(userId, categoryId) : null;
            String currency = resolveCurrency(currencyParam, account.getCurrency());

            Set<String> seenInFile = new HashSet<>();
            List<ParsedRow> parsedRows = new ArrayList<>();
            for (RowDraft d : drafts) {
                if (d.error() != null) {
                    parsedRows.add(new ParsedRow(d.rowNumber(), d.date(), d.amount(), currency, d.type(), null,
                            d.raw(), null, false, null, d.error()));
                    continue;
                }
                String fingerprint = buildFingerprint(
                        userId, d.date().atStartOfDay(), d.amount(), currency, d.type(), d.merchant(), d.description());
                String duplicateReason = null;
                if (transactionRepository.existsByUserIdAndFingerprint(userId, fingerprint)) {
                    duplicateReason = "Already in your transactions";
                } else if (!seenInFile.add(fingerprint)) {
                    duplicateReason = "Repeated earlier in this file";
                }
                parsedRows.add(new ParsedRow(d.rowNumber(), d.date(), d.amount(), currency, d.type(), d.merchant(),
                        d.description(), fingerprint, duplicateReason != null, duplicateReason, null));
            }

            int duplicateCount = (int) parsedRows.stream().filter(r -> r.error() == null && r.duplicate()).count();
            int invalidCount = (int) parsedRows.stream().filter(r -> r.error() != null).count();
            int validCount = parsedRows.size() - invalidCount;

            return new ImportPreviewResponse(
                    fileType,
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

        public ImportResultResponse confirmImport(UUID userId, ConfirmImportRequest request) {
            User user = getUserOr404(userId);
            Account account = resolveAccount(userId, request.accountId());
            Category defaultCategory = request.categoryId() != null ? resolveCategory(userId, request.categoryId()) : null;
            String currency = resolveCurrency(request.currency(), account.getCurrency());
            boolean skipDuplicates = request.skipDuplicates() == null || request.skipDuplicates();

            if (request.rows() == null || request.rows().isEmpty()) {
                throw new IllegalArgumentException("No rows provided to import");
            }

            List<String> fingerprints = new ArrayList<>();
            List<Transaction> pending = new ArrayList<>();
            Set<String> acceptedInRequest = new HashSet<>();
            int skippedDuplicates = 0;

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
                        userId, txDateTime, row.amount(), currency, type, row.merchant(), row.description());
                fingerprints.add(fingerprint);

                boolean duplicate = transactionRepository.existsByUserIdAndFingerprint(userId, fingerprint)
                        || acceptedInRequest.contains(fingerprint);
                if (duplicate && skipDuplicates && !Boolean.TRUE.equals(row.allowDuplicate())) {
                    skippedDuplicates++;
                    continue;
                }
                acceptedInRequest.add(fingerprint);

                Category category = row.categoryId() != null ? resolveCategory(userId, row.categoryId())
                        : defaultCategory != null && defaultCategory.getType() == type ? defaultCategory
                        : categoryService.fallback(type);
                if (category.getType() != type) {
                    throw new IllegalArgumentException("Category '" + category.getName() + "' is for "
                            + category.getType() + " transactions");
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
                pending.add(tx);
            }

            ImportBatch batch = new ImportBatch();
            batch.setUser(user);
            batch.setFileName(request.fileName() != null && !request.fileName().isBlank() ? request.fileName().trim() : "import");
            batch.setFileHash(sha256(String.join(",", fingerprints)));
            batch.setTotalRows(request.rows().size());
            batch.setImportedRows(pending.size());
            batch.setImportStatus(skippedDuplicates == 0 ? EImportStatus.COMPLETED : EImportStatus.PARTIALLY_COMPLETED);
            ImportBatch savedBatch = importBatchRepository.save(batch);

            List<UUID> createdIds = new ArrayList<>();
            for (Transaction tx : pending) {
                tx.setImportBatch(savedBatch);
                createdIds.add(transactionRepository.save(tx).getId());
            }

            return new ImportResultResponse(savedBatch.getId(), pending.size(), skippedDuplicates, createdIds);
        }

        private RowDraft parseLine(int rowNumber, String line) {
            // Expected columns: date,amount,type,merchant,description (type, merchant, description optional)
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
            }

            if (parts.length < 2) {
                return error(rowNumber, line, null, "Expected at least date and amount columns");
            }

            LocalDate date = StatementTextParser.parseDate(parts[0]);
            if (date == null) {
                return error(rowNumber, line, null, "Invalid date '" + parts[0] + "' (use YYYY-MM-DD)");
            }

            long amount;
            try {
                amount = MoneyParser.parseWholeUnits(parts[1]);
            } catch (Exception ex) {
                return error(rowNumber, line, date, "Invalid amount '" + parts[1] + "'");
            }
            if (amount == 0L) {
                return error(rowNumber, line, date, "Amount must not be zero");
            }

            // Bank exports often write debits as negative numbers; the sign is dropped and the row stays an expense.
            ETransactionType type = ETransactionType.EXPENSE;
            if (parts.length >= 3 && !parts[2].isBlank()) {
                try {
                    type = ETransactionType.valueOf(parts[2].trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    return error(rowNumber, line, date, "Invalid transaction type '" + parts[2] + "' (use INCOME or EXPENSE)");
                }
            }

            String merchant = (parts.length >= 4 && !parts[3].isBlank()) ? parts[3].trim() : null;
            String description = (parts.length >= 5 && !parts[4].isBlank()) ? parts[4].trim() : null;
            return new RowDraft(rowNumber, date, Math.abs(amount), type, merchant, description, null, line);
        }

        private static RowDraft error(int rowNumber, String line, LocalDate date, String message) {
            return new RowDraft(rowNumber, date, 0L, null, null, null, message, line);
        }

        private boolean looksLikeHeader(String firstLine) {
            String lower = firstLine.toLowerCase();
            return lower.contains("date") && lower.contains("amount");
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

        private Category resolveCategory(UUID userId, UUID categoryId) {
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
                UUID userId,
                LocalDateTime dateTime,
                Long amount,
                String currency,
                ETransactionType type,
                String merchant,
                String note) {
            return sha256(String.join("|",
                    String.valueOf(userId),
                    dateTime.toLocalDate().toString(),
                    String.valueOf(amount),
                    currency != null ? currency : "RWF",
                    type != null ? type.name() : "",
                    merchant != null ? merchant.trim().toLowerCase() : "",
                    note != null ? note.trim().toLowerCase() : ""));
        }

        private static String sha256(String raw) {
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
