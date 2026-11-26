package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.DTO.TransactionDTO.GenerateRecurringResult;
import com.example.ExpenseTracker.DTO.TransactionDTO.TransactionRequest;
import com.example.ExpenseTracker.DTO.TransactionDTO.TransactionResponse;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Add, update, delete, filter and generate recurring transactions")
public class TransactionController {

    private final TransactionService transactionService;

        public TransactionController(TransactionService transactionService) {
            this.transactionService = transactionService;
        }

        @PostMapping
        public ResponseEntity<TransactionResponse> create(
                @AuthenticationPrincipal UUID userId,
                @Valid @RequestBody TransactionRequest request) {
            TransactionResponse created = transactionService.create(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }

        @GetMapping
        public ResponseEntity<List<TransactionResponse>> list(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @RequestParam(required = false) ETransactionType type,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) UUID categoryId,
                @RequestParam(required = false) UUID accountId,
                @RequestParam(required = false) Boolean recurring) {
            return ResponseEntity.ok(
                    transactionService.list(userId, from, to, type, currency, categoryId, accountId, recurring));
        }

        @GetMapping("/{id}")
        public ResponseEntity<TransactionResponse> getById(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID id) {
            return ResponseEntity.ok(transactionService.getById(userId, id));
        }

        @PutMapping("/{id}")
        public ResponseEntity<TransactionResponse> update(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID id,
                @Valid @RequestBody TransactionRequest request) {
            return ResponseEntity.ok(transactionService.update(userId, id, request));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID id) {
            transactionService.delete(userId, id);
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/recurring/generate")
        public ResponseEntity<GenerateRecurringResult> generateRecurring(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate upToDate) {
            return ResponseEntity.ok(transactionService.generateRecurring(userId, upToDate));
        }
    }
;
