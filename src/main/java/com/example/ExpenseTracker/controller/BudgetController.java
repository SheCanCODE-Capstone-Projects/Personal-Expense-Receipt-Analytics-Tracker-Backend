package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetRequest;
import com.example.ExpenseTracker.DTO.BudgetDTO.BudgetStatus;
import com.example.ExpenseTracker.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;



@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

        public BudgetController(BudgetService budgetService) {
            this.budgetService = budgetService;
        }

        @PostMapping
        public ResponseEntity<BudgetStatus> saveBudget(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam(required = false) String currency,
                @Valid @RequestBody BudgetRequest request) {
            return ResponseEntity.ok(budgetService.saveBudget(userId, request, currency));
        }

        @GetMapping
        public ResponseEntity<List<BudgetStatus>> listByMonth(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam String month,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(budgetService.listByMonth(userId, month, currency));
        }

        @GetMapping("/{id}")
        public ResponseEntity<BudgetStatus> getById(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long id,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(budgetService.getById(userId, id, currency));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long id) {
            budgetService.delete(userId, id);
            return ResponseEntity.noContent().build();
        }
    };

