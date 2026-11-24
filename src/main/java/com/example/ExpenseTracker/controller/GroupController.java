package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.DTO.GroupDTO.AddExpenseRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddMemberRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.BalanceResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.CreateGroupRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.ExpenseResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.MemberResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.SettlementRequest;
import com.example.ExpenseTracker.Model.Group;
import com.example.ExpenseTracker.Model.Settlement;
import com.example.ExpenseTracker.core.SettlementCalculator;
import com.example.ExpenseTracker.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/groups")
public class GroupController {
 private final GroupService groupService;

        public GroupController(GroupService groupService) {
            this.groupService = groupService;
        }

        @PostMapping
        public ResponseEntity<MemberResponse> createGroup(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @Valid @RequestBody CreateGroupRequest request) {
            MemberResponse created = groupService.createGroup(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }

        @GetMapping
        public ResponseEntity<List<Group>> listUserGroups(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
            return ResponseEntity.ok(groupService.listUserGroups(userId));
        }

        @PostMapping("/{groupId}/members")
        public ResponseEntity<MemberResponse> addMember(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId,
                @Valid @RequestBody AddMemberRequest request) {
            MemberResponse added = groupService.addMember(userId, groupId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(added);
        }

        @GetMapping("/{groupId}/members")
        public ResponseEntity<List<MemberResponse>> listMembers(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId) {
            return ResponseEntity.ok(groupService.listMembers(userId, groupId));
        }

        @PostMapping("/{groupId}/expenses")
        public ResponseEntity<ExpenseResponse> addExpense(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId,
                @RequestParam(required = false) String currency,
                @Valid @RequestBody AddExpenseRequest request) {
            ExpenseResponse created = groupService.addExpense(userId, groupId, request, currency);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }

        @GetMapping("/{groupId}/balances")
        public ResponseEntity<List<BalanceResponse>> getBalances(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId) {
            return ResponseEntity.ok(groupService.getBalances(userId, groupId));
        }

        @GetMapping("/{groupId}/settlements/suggestions")
        public ResponseEntity<List<SettlementCalculator.Transfer>> suggestSettlements(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId) {
            return ResponseEntity.ok(groupService.suggestSettlements(userId, groupId));
        }

        @PostMapping("/{groupId}/settlements")
        public ResponseEntity<Settlement> recordSettlement(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @PathVariable Long groupId,
                @Valid @RequestBody SettlementRequest request) {
            Settlement saved = groupService.recordSettlement(userId, groupId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }
    };

