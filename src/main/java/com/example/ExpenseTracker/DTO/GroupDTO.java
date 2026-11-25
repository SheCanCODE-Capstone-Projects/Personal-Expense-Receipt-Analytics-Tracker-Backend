package com.example.ExpenseTracker.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class GroupDTO {
    private GroupDTO() {}

    public record CreateGroupRequest(@NotBlank String name) {}

    public record AddMemberRequest(String email, String displayName) {}

    public record Participant(@NotNull UUID memberId, BigDecimal value) {}

    public record AddExpenseRequest(
            @NotBlank String description,
            @NotNull @Positive Long totalAmount,
            @NotNull UUID paidByMemberId,
            LocalDate date,
            List<Participant> participants) {}

    public record SettlementRequest(
            @NotNull UUID fromMemberId,
            @NotNull UUID toMemberId,
            @NotNull @Positive Long amount,
            String currency) {}

    public record GroupResponse(UUID id, String name, String description, UUID createdByUserId, LocalDateTime createdAt) {}

    public record SettlementResponse(
            UUID id, UUID fromMemberId, UUID toMemberId, long amount, String currency,
            String status, LocalDateTime settledAt) {}

    public record MemberResponse(UUID id, String displayName, UUID userId) {}

    public record ShareResponse(UUID memberId, String memberName, long amount) {}

    public record ExpenseResponse(
            UUID id, String description, long totalAmount, UUID paidByMemberId,
            String paidByName, String splitType, LocalDate date, String currency, List<ShareResponse> shares) {}
    public record BalanceResponse(UUID memberId, String memberName, String currency, long paid, long owed, long net) {}
}