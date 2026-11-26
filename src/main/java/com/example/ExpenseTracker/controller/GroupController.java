package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.DTO.GroupDTO.AddExpenseRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddMemberRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.BalanceResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.CreateGroupRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.ExpenseResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.GroupResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.MemberResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.SettlementRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.SettlementResponse;
import com.example.ExpenseTracker.core.SettlementCalculator;
import com.example.ExpenseTracker.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
 private final GroupService groupService;

        public GroupController(GroupService groupService) {
            this.groupService = groupService;
        }

        @PostMapping
        public ResponseEntity<MemberResponse> createGroup(
                @AuthenticationPrincipal UUID userId,
                @Valid @RequestBody CreateGroupRequest request) {
            MemberResponse created = groupService.createGroup(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }

        @GetMapping
        public ResponseEntity<List<GroupResponse>> listUserGroups(
                @AuthenticationPrincipal UUID userId) {
            return ResponseEntity.ok(groupService.listUserGroups(userId));
        }

        @PostMapping("/{groupId}/members")
        public ResponseEntity<MemberResponse> addMember(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId,
                @Valid @RequestBody AddMemberRequest request) {
            MemberResponse added = groupService.addMember(userId, groupId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(added);
        }

        @GetMapping("/{groupId}/members")
        public ResponseEntity<List<MemberResponse>> listMembers(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId) {
            return ResponseEntity.ok(groupService.listMembers(userId, groupId));
        }

        @PostMapping("/{groupId}/expenses")
        public ResponseEntity<ExpenseResponse> addExpense(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId,
                @RequestParam(required = false) String currency,
                @Valid @RequestBody AddExpenseRequest request) {
            ExpenseResponse created = groupService.addExpense(userId, groupId, request, currency);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }

        @GetMapping("/{groupId}/expenses")
        public ResponseEntity<List<ExpenseResponse>> listExpenses(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId) {
            return ResponseEntity.ok(groupService.listExpenses(userId, groupId));
        }

        @GetMapping("/{groupId}/balances")
        public ResponseEntity<List<BalanceResponse>> getBalances(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(groupService.getBalances(userId, groupId, currency));
        }

        @GetMapping("/{groupId}/settlements/suggestions")
        public ResponseEntity<List<SettlementCalculator.Transfer>> suggestSettlements(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId,
                @RequestParam(required = false) String currency) {
            return ResponseEntity.ok(groupService.suggestSettlements(userId, groupId, currency));
        }

        @GetMapping("/{groupId}/settlements")
        public ResponseEntity<List<SettlementResponse>> listSettlements(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId) {
            return ResponseEntity.ok(groupService.listSettlements(userId, groupId));
        }

        @PostMapping("/{groupId}/settlements")
        public ResponseEntity<SettlementResponse> recordSettlement(
                @AuthenticationPrincipal UUID userId,
                @PathVariable UUID groupId,
                @Valid @RequestBody SettlementRequest request) {
            SettlementResponse saved = groupService.recordSettlement(userId, groupId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }
    };

