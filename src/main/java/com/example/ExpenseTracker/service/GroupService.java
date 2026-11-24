package com.example.ExpenseTracker.service;

import com.example.ExpenseTracker.DTO.GroupDTO.AddExpenseRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.AddMemberRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.BalanceResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.CreateGroupRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.ExpenseResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.MemberResponse;
import com.example.ExpenseTracker.DTO.GroupDTO.SettlementRequest;
import com.example.ExpenseTracker.DTO.GroupDTO.ShareResponse;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
import com.example.ExpenseTracker.Model.ERole;
import com.example.ExpenseTracker.Model.ESettlementStatus;
import com.example.ExpenseTracker.Model.ExpenseShare;
import com.example.ExpenseTracker.Model.Group;
import com.example.ExpenseTracker.Model.Membership;
import com.example.ExpenseTracker.Model.Settlement;
import com.example.ExpenseTracker.Model.SharedExpense;
import com.example.ExpenseTracker.Model.User;
import com.example.ExpenseTracker.Repository.ExpenseShareRepository;
import com.example.ExpenseTracker.Repository.GroupRepository;
import com.example.ExpenseTracker.Repository.MembershipRepository;
import com.example.ExpenseTracker.Repository.SettlementRepository;
import com.example.ExpenseTracker.Repository.SharedExpenseRepository;
import com.example.ExpenseTracker.Repository.UserRepository;
import com.example.ExpenseTracker.core.SettlementCalculator;
import com.example.ExpenseTracker.core.SplitCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final SharedExpenseRepository sharedExpenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;

    public GroupService(
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            SharedExpenseRepository sharedExpenseRepository,
            ExpenseShareRepository expenseShareRepository,
            SettlementRepository settlementRepository,
            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.sharedExpenseRepository = sharedExpenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
    }

    public MemberResponse createGroup(Long creatorUserId, CreateGroupRequest request) {
        User creator = getUserOr404(creatorUserId);

        Group group = new Group();
        group.setName(request.name().trim());
        group.setCreatedBy(creator);
        Group savedGroup = groupRepository.save(group);

        Membership ownerMembership = new Membership();
        ownerMembership.setGroup(savedGroup);
        ownerMembership.setUser(creator);
        ownerMembership.setRole(ERole.OWNER);
        Membership savedMembership = membershipRepository.save(ownerMembership);

        return toMemberResponse(savedMembership, null);
    }

    @Transactional(readOnly = true)
    public List<Group> listUserGroups(Long userId) {
        getUserOr404(userId);
        return membershipRepository.findByUserId(userId).stream()
                .map(Membership::getGroup)
                .distinct()
                .toList();
    }

    public MemberResponse addMember(Long actorUserId, Long groupId, AddMemberRequest request) {
        Group group = getGroupForMemberOr404(actorUserId, groupId);

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Member email is required");
        }

        User targetUser = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + request.email()));

        if (membershipRepository.existsByGroupIdAndUserId(groupId, targetUser.getId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        Membership membership = new Membership();
        membership.setGroup(group);
        membership.setUser(targetUser);
        membership.setRole(ERole.MEMBER);
        Membership saved = membershipRepository.save(membership);

        return toMemberResponse(saved, request.displayName());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long actorUserId, Long groupId) {
        getGroupForMemberOr404(actorUserId, groupId);
        return membershipRepository.findByGroupIdOrderById(groupId).stream()
                .map(m -> toMemberResponse(m, null))
                .toList();
    }

    public ExpenseResponse addExpense(Long actorUserId, Long groupId, AddExpenseRequest request, String currencyParam) {
        Group group = getGroupForMemberOr404(actorUserId, groupId);

        List<Membership> members = membershipRepository.findByGroupIdOrderById(groupId);
        Map<Long, Membership> membersById = new HashMap<>();
        Map<Long, Long> userIdToMemberId = new HashMap<>();
        for (Membership m : members) {
            membersById.put(m.getId(), m);
            userIdToMemberId.put(m.getUser().getId(), m.getId());
        }

        Membership payerMembership = membersById.get(request.paidByMemberId());
        if (payerMembership == null) {
            throw new ResourceNotFoundException("Payer member not found in group");
        }

        List<SplitCalculator.ShareInput> inputs;
        if (request.participants() == null || request.participants().isEmpty()) {
            inputs = members.stream()
                    .map(m -> new SplitCalculator.ShareInput(m.getId(), null))
                    .toList();
        } else {
            Set<Long> seenParticipantIds = new HashSet<>();
            for (var p : request.participants()) {
                if (!membersById.containsKey(p.memberId())) {
                    throw new ResourceNotFoundException("Participant member not found in group: " + p.memberId());
                }
                if (!seenParticipantIds.add(p.memberId())) {
                    throw new IllegalArgumentException("Duplicate participant member ID: " + p.memberId());
                }
            }
            inputs = request.participants().stream()
                    .map(p -> new SplitCalculator.ShareInput(p.memberId(), p.value()))
                    .toList();
        }

        SplitCalculator.SplitOutcome outcome = SplitCalculator.calculate(request.totalAmount(), inputs);

        LocalDate expenseDate = request.date() != null ? request.date() : LocalDate.now();
        String currency = (currencyParam == null || currencyParam.isBlank())
                ? "RWF"
                : currencyParam.trim().toUpperCase();

        SharedExpense expense = new SharedExpense();
        expense.setGroup(group);
        expense.setPayer(payerMembership.getUser());
        expense.setDescription(request.description().trim());
        expense.setTotalAmount(request.totalAmount());
        expense.setCurrency(currency);
        expense.setExpenseDate(expenseDate.atStartOfDay());
        SharedExpense savedExpense = sharedExpenseRepository.save(expense);

        List<ShareResponse> shareResponses = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : outcome.amountsByParticipant().entrySet()) {
            Membership participantMembership = membersById.get(entry.getKey());
            long shareAmt = entry.getValue();

            ExpenseShare share = new ExpenseShare();
            share.setSharedExpense(savedExpense);
            share.setUser(participantMembership.getUser());
            share.setShareAmount(shareAmt);
            expenseShareRepository.save(share);

            shareResponses.add(new ShareResponse(
                    participantMembership.getId(),
                    participantMembership.getUser().getFullName(),
                    shareAmt));
        }

        return new ExpenseResponse(
                savedExpense.getId(),
                savedExpense.getDescription(),
                savedExpense.getTotalAmount(),
                payerMembership.getId(),
                payerMembership.getUser().getFullName(),
                outcome.splitType(),
                expenseDate,
                shareResponses);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(Long actorUserId, Long groupId) {
        getGroupForMemberOr404(actorUserId, groupId);

        List<Membership> members = membershipRepository.findByGroupIdOrderById(groupId);
        Map<Long, Long> userIdToMemberId = new HashMap<>();
        for (Membership m : members) {
            userIdToMemberId.put(m.getUser().getId(), m.getId());
        }

        List<SharedExpense> expenses = sharedExpenseRepository.findByGroupIdOrderByExpenseDateDescIdDesc(groupId);
        if (expenses.isEmpty()) {
            return List.of();
        }

        List<Long> expenseIds = expenses.stream().map(SharedExpense::getId).toList();
        List<ExpenseShare> allShares = expenseShareRepository.findBySharedExpenseIdIn(expenseIds);

        Map<Long, List<ShareResponse>> sharesByExpenseId = new HashMap<>();
        for (ExpenseShare s : allShares) {
            Long expId = s.getSharedExpense().getId();
            Long memberId = userIdToMemberId.get(s.getUser().getId());
            long amt = s.getShareAmount() != null ? s.getShareAmount() : 0L;
            sharesByExpenseId
                    .computeIfAbsent(expId, k -> new ArrayList<>())
                    .add(new ShareResponse(memberId, s.getUser().getFullName(), amt));
        }

        return expenses.stream()
                .map(exp -> new ExpenseResponse(
                        exp.getId(),
                        exp.getDescription(),
                        exp.getTotalAmount() != null ? exp.getTotalAmount() : 0L,
                        userIdToMemberId.get(exp.getPayer().getId()),
                        exp.getPayer().getFullName(),
                        "RECORDED",
                        exp.getExpenseDate() != null ? exp.getExpenseDate().toLocalDate() : LocalDate.now(),
                        sharesByExpenseId.getOrDefault(exp.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getBalances(Long actorUserId, Long groupId) {
        getGroupForMemberOr404(actorUserId, groupId);

        List<Membership> members = membershipRepository.findByGroupIdOrderById(groupId);
        Map<Long, Long> userIdToMemberId = new HashMap<>();
        Map<Long, Long> paidByMember = new HashMap<>();
        Map<Long, Long> owedByMember = new HashMap<>();

        for (Membership m : members) {
            userIdToMemberId.put(m.getUser().getId(), m.getId());
            paidByMember.put(m.getId(), 0L);
            owedByMember.put(m.getId(), 0L);
        }

        List<SharedExpense> expenses = sharedExpenseRepository.findByGroupIdOrderByExpenseDateDescIdDesc(groupId);
        List<Long> expenseIds = new ArrayList<>();

        for (SharedExpense exp : expenses) {
            expenseIds.add(exp.getId());
            Long payerMemberId = userIdToMemberId.get(exp.getPayer().getId());
            if (payerMemberId != null) {
                long amt = exp.getTotalAmount() != null ? exp.getTotalAmount() : 0L;
                paidByMember.merge(payerMemberId, amt, Long::sum);
            }
        }

        if (!expenseIds.isEmpty()) {
            List<ExpenseShare> shares = expenseShareRepository.findBySharedExpenseIdIn(expenseIds);
            for (ExpenseShare share : shares) {
                Long memberId = userIdToMemberId.get(share.getUser().getId());
                if (memberId != null) {
                    long amt = share.getShareAmount() != null ? share.getShareAmount() : 0L;
                    owedByMember.merge(memberId, amt, Long::sum);
                }
            }
        }

        List<Settlement> settlements = settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        for (Settlement s : settlements) {
            if (s.getStatus() == ESettlementStatus.SETTLED) {
                Long fromMemberId = userIdToMemberId.get(s.getFromUser().getId());
                Long toMemberId = userIdToMemberId.get(s.getToUser().getId());
                long amt = s.getAmount() != null ? s.getAmount() : 0L;
                if (fromMemberId != null) {
                    paidByMember.merge(fromMemberId, amt, Long::sum);
                }
                if (toMemberId != null) {
                    owedByMember.merge(toMemberId, amt, Long::sum);
                }
            }
        }

        return members.stream()
                .map(m -> {
                    long paid = paidByMember.getOrDefault(m.getId(), 0L);
                    long owed = owedByMember.getOrDefault(m.getId(), 0L);
                    long net = paid - owed;
                    return new BalanceResponse(
                            m.getId(),
                            m.getUser().getFullName(),
                            paid,
                            owed,
                            net);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementCalculator.Transfer> suggestSettlements(Long actorUserId, Long groupId) {
        List<BalanceResponse> balances = getBalances(actorUserId, groupId);
        Map<Long, Long> netByMemberId = new HashMap<>();
        for (BalanceResponse b : balances) {
            netByMemberId.put(b.memberId(), b.net());
        }
        return SettlementCalculator.settle(netByMemberId);
    }

    public Settlement recordSettlement(Long actorUserId, Long groupId, SettlementRequest request) {
        Group group = getGroupForMemberOr404(actorUserId, groupId);

        List<Membership> members = membershipRepository.findByGroupIdOrderById(groupId);
        Map<Long, Membership> membersById = new HashMap<>();
        for (Membership m : members) {
            membersById.put(m.getId(), m);
        }

        Membership fromMember = membersById.get(request.fromMemberId());
        Membership toMember = membersById.get(request.toMemberId());
        if (fromMember == null || toMember == null) {
            throw new ResourceNotFoundException("Settlement member not found in group");
        }
        if (fromMember.getId().equals(toMember.getId())) {
            throw new IllegalArgumentException("From member and to member must be different");
        }

        Settlement settlement = new Settlement();
        settlement.setGroup(group);
        settlement.setFromUser(fromMember.getUser());
        settlement.setToUser(toMember.getUser());
        settlement.setAmount(request.amount());
        settlement.setStatus(ESettlementStatus.SETTLED);
        settlement.setSettledAt(LocalDateTime.now());

        return settlementRepository.save(settlement);
    }

    @Transactional(readOnly = true)
    public List<Settlement> listSettlements(Long actorUserId, Long groupId) {
        getGroupForMemberOr404(actorUserId, groupId);
        return settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    private User getUserOr404(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Group getGroupForMemberOr404(Long userId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        return group;
    }

    private MemberResponse toMemberResponse(Membership membership, String preferredDisplayName) {
        String name = (preferredDisplayName != null && !preferredDisplayName.isBlank())
                ? preferredDisplayName.trim()
                : membership.getUser().getFullName();
        return new MemberResponse(
                membership.getId(),
                name,
                membership.getUser().getId());
    }
}