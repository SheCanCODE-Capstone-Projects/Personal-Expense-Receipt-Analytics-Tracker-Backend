package com.example.ExpenseTracker.Controller;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Model.Transaction;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

    @RestController
    @RequestMapping("/api/transactions")
    public class TransactionController {

        private final TransactionService transactionService;

        public TransactionController(TransactionService transactionService) {
            this.transactionService = transactionService;
        }

        @PostMapping
        public ResponseEntity<TransactionResponse> create(
                @RequestParam Long userId,
                @Valid @RequestBody CreateTransactionRequest request
        ) {
            Transaction entity = new Transaction();
            entity.setAmount(request.amount());
            entity.setTransactionType(request.transactionType());
            entity.setDescription(request.description());
            entity.setMerchant(request.merchant());
            entity.setPaymentMethod(request.paymentMethod());
            entity.setCurrency(request.currency());
            entity.setRecurring(request.recurring());
            entity.setRecurrenceFrequency(request.recurrenceFrequency());
            entity.setTransactionDate(request.transactionDate());

            Transaction created = transactionService.createTransaction(
                    entity,
                    userId,
                    request.accountId(),
                    request.categoryId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(created));
        }

        @GetMapping
        public ResponseEntity<List<TransactionResponse>> list(
                @RequestParam Long userId,
                @RequestParam(required = false) ETransactionType type,
                @RequestParam(required = false) Long categoryId,
                @RequestParam(required = false) Long accountId,
                @RequestParam(required = false) String merchant,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
        ) {
            List<TransactionResponse> results = transactionService
                    .getTransactions(userId, type, categoryId, accountId, merchant, startDate, endDate)
                    .stream()
                    .map(TransactionResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        }

        @GetMapping("/{id}")
        public ResponseEntity<TransactionResponse> getById(
                @PathVariable Long id,
                @RequestParam Long userId
        ) {
            Transaction tx = transactionService.getTransactionById(id, userId);
            return ResponseEntity.ok(TransactionResponse.from(tx));
        }

        @PutMapping("/{id}")
        public ResponseEntity<TransactionResponse> update(
                @PathVariable Long id,
                @RequestParam Long userId,
                @Valid @RequestBody UpdateTransactionRequest request
        ) {
            Transaction entity = new Transaction();
            entity.setAmount(request.amount());
            entity.setTransactionType(request.transactionType());
            entity.setDescription(request.description());
            entity.setMerchant(request.merchant());
            entity.setPaymentMethod(request.paymentMethod());
            entity.setCurrency(request.currency());
            entity.setRecurring(request.recurring());
            entity.setRecurrenceFrequency(request.recurrenceFrequency());
            entity.setTransactionDate(request.transactionDate());

            Transaction updated = transactionService.updateTransaction(
                    id,
                    entity,
                    userId,
                    request.accountId(),
                    request.categoryId()
            );
            return ResponseEntity.ok(TransactionResponse.from(updated));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(
                @PathVariable Long id,
                @RequestParam Long userId
        ) {
            transactionService.deleteTransaction(id, userId);
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/{id}/next-recurring")
        public ResponseEntity<TransactionResponse> generateNextRecurring(
                @PathVariable Long id,
                @RequestParam Long userId
        ) {
            Transaction next = transactionService.generateNextMonthlyTransaction(id, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(next));
        }
    }

