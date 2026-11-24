package com.example.ExpenseTracker.controller;

import com.example.ExpenseTracker.DTO.BudgetDTO.DashboardResponse;
import com.example.ExpenseTracker.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
 private final DashboardService dashboardService;

        public DashboardController(DashboardService dashboardService) {
            this.dashboardService = dashboardService;
        }

        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<DashboardResponse> getMonthlyDashboard(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam(required = false) String month,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(dashboardService.getMonthlyDashboard(userId, month, currency));
        }
    };

