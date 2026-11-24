package com.example.ExpenseTracker.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GroupDTO {
    private GroupDTO() {}

    public record CreateGroupRequest(@NotBlank String name) {}

    public record AddMemberRequest(String email, String displayName) {}

    public record Participant(@NotNull Long memberId, BigDecimal value) {}

    public record AddExpenseRequest(
            @NotBlank String description,
            @NotNull @Positive Long totalAmount,
            @NotNull Long paidByMemberId,
            LocalDate date,
            List<Participant> participants) {}

    public record SettlementRequest(
            @NotNull Long fromMemberId,
            @NotNull Long toMemberId,
            @NotNull @Positive Long amount) {}

    public record MemberResponse(Long id, String displayName, Long userId) {}

    public record ShareResponse(Long memberId, String memberName, long amount) {}

    public record ExpenseResponse(
            Long id, String description, long totalAmount, Long paidByMemberId,
            String paidByName, String splitType, LocalDate date, List<ShareResponse> shares) {}
    public record BalanceResponse(Long memberId, String memberName, long paid, long owed, long net) {}
}