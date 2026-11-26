package com.example.ExpenseTracker.controller;

import com.example.ExpenseTracker.DTO.BudgetDTO.DashboardResponse;
import com.example.ExpenseTracker.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
 private final DashboardService dashboardService;

        public DashboardController(DashboardService dashboardService) {
            this.dashboardService = dashboardService;
        }

        @GetMapping
        public ResponseEntity<DashboardResponse> getMonthlyDashboard(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) String month,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(dashboardService.getMonthlyDashboard(userId, month, currency));
        }
    };

