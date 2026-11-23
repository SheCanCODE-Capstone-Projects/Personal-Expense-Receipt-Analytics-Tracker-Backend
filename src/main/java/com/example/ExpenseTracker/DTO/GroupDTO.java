package com.example.ExpenseTracker.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class GroupDTO {
    private GroupDTO() {}

    public record CreateGroupRequest(@NotBlank String name) {}

    record AddMemberRequest(String email, String displayName) {}

    public record Participant(@NotNull Long memberId, BigDecimal value) {}

    public record AddExpenseRequest(
            @NotBlank String description,
            @NotNull @Positive Long totalAmount,
            @NotNull Long paidByMemberId,
            LocalDate date,
            List<Participant> participants) {}

    public record SettlementRequest(@NotNull Long fromMemberId, @NotNull Long toMemberId, @NotNull @Positive Long amount) {}

    public record MemberResponse(Long id, String displayName, Long userId) {}

    public record ShareResponse(Long memberId, String memberName, long amount) {}

    public record ExpenseResponse(Long id, String description, long totalAmount, Long paidByMemberId,
                                  String paidByName, String splitType, LocalDate date, List<ShareResponse> shares) {}

    /** net > 0: the group owes this member. net < 0: this member owes the group. */
    public record BalanceResponse(Long memberId, String memberName, long paid, long owed,
                                  long settlementsSent, long settlementsReceived, long net) {}

    public record PaymentResponse(Long fromMemberId, String fromName, Long toMemberId, String toName, long amount) {}

    public record SettlementResponse(Long id, Long fromMemberId, String fromName, Long toMemberId,
                                     String toName, long amount, String createdAt) {}

    public record GroupSummary(Long id, String name, String currency, int memberCount, long myNetBalance) {}

    public record GroupDetail(Long id, String name, String currency,
                              List<MemberResponse> members,
                              List<ExpenseResponse> expenses,
                              List<BalanceResponse> balances,
                              long balancesSum,
                              List<PaymentResponse> proposedSettlements,
                              String settlementMethod,
                              List<SettlementResponse> settlements) {}
}


