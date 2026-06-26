package com.hrm.system.service;

import com.hrm.system.dto.LeavePolicyDto;
import com.hrm.system.model.LeaveBalance;
import com.hrm.system.model.LeavePolicy;
import com.hrm.system.model.User;
import com.hrm.system.repository.LeaveBalanceRepository;
import com.hrm.system.repository.LeavePolicyRepository;
import com.hrm.system.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeavePolicyService {

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Seed default policies on first boot (only if table is empty)
    // ─────────────────────────────────────────────────────────────────────────
    @PostConstruct
    @Transactional
    public void seedDefaultPolicies() {
        seedIfAbsent("SICK",        10, false, false, 0,  false, null);
        seedIfAbsent("CASUAL",      12, false, false, 0,  false, null);
        seedIfAbsent("ANNUAL",      21, true,  true,  7,  false, null);
        // Eid leaves: 3 days each, can apply up to 2 days before the event
        seedIfAbsent("EIDULFITAR",  3,  false, false, 0,  true,  2);
        seedIfAbsent("EIDULAZHA",   3,  false, false, 0,  true,  2);
    }

    private void seedIfAbsent(String type, int days, boolean requiresOneYear,
                              boolean carryForward, int maxCarryForward,
                              boolean isPublicHoliday, Integer applyBeforeDays) {
        if (!leavePolicyRepository.existsByLeaveType(type)) {
            leavePolicyRepository.save(LeavePolicy.builder()
                    .leaveType(type)
                    .totalDaysPerYear(days)
                    .requiresOneYear(requiresOneYear)
                    .carryForward(carryForward)
                    .maxCarryForwardDays(maxCarryForward)
                    .isPublicHoliday(isPublicHoliday)
                    .applyBeforeDays(applyBeforeDays)
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yearly Cron Job — runs at midnight on January 1st every year
    // Generates fresh balances and processes carry-forwards
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void processYearlyLeaveReset() {
        int newYear = LocalDate.now().getYear();
        int prevYear = newYear - 1;

        List<User> allUsers = userRepository.findAll();
        List<LeavePolicy> policies = leavePolicyRepository.findAll();

        for (User user : allUsers) {
            for (LeavePolicy policy : policies) {

                // Skip annual leave for employees with < 1 year of service
                if (policy.getRequiresOneYear() && !hasCompletedOneYear(user)) {
                    continue;
                }

                int carryForward = 0;

                if (policy.getCarryForward()) {
                    // Fetch last year's balance to compute carry-forward
                    carryForward = leaveBalanceRepository
                            .findByUserIdAndLeaveTypeAndYear(user.getId(), policy.getLeaveType(), prevYear)
                            .map(prev -> {
                                int remaining = prev.getTotalDays() - prev.getUsedDays() - prev.getPendingDays();
                                return Math.min(Math.max(remaining, 0), policy.getMaxCarryForwardDays());
                            })
                            .orElse(0);
                }

                int finalCarryForward = carryForward;

                // Create or reset balance for new year
                LeaveBalance newBalance = leaveBalanceRepository
                        .findByUserIdAndLeaveTypeAndYear(user.getId(), policy.getLeaveType(), newYear)
                        .orElseGet(() -> LeaveBalance.builder()
                                .user(user)
                                .leaveType(policy.getLeaveType())
                                .year(newYear)
                                .usedDays(0)
                                .pendingDays(0)
                                .build());

                newBalance.setCarryForwardDays(finalCarryForward);
                newBalance.setTotalDays(policy.getTotalDaysPerYear() + finalCarryForward);
                newBalance.setUsedDays(0);
                newBalance.setPendingDays(0);

                leaveBalanceRepository.save(newBalance);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Called when a NEW employee is created — seed their initial balances
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void initializeBalancesForNewEmployee(User user) {
        int currentYear = LocalDate.now().getYear();
        List<LeavePolicy> policies = leavePolicyRepository.findAll();

        for (LeavePolicy policy : policies) {

            // Annual leave requires 1 year of service — skip for new employees
            if (policy.getRequiresOneYear()) continue;

            boolean alreadyExists = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeAndYear(user.getId(), policy.getLeaveType(), currentYear)
                    .isPresent();

            if (!alreadyExists) {
                leaveBalanceRepository.save(LeaveBalance.builder()
                        .user(user)
                        .leaveType(policy.getLeaveType())
                        .year(currentYear)
                        .totalDays(policy.getTotalDaysPerYear())
                        .usedDays(0)
                        .pendingDays(0)
                        .carryForwardDays(0)
                        .build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Daily Cron — runs at 1:00 AM every day
    // Creates balances for policies requiring 1 year of service (e.g. ANNUAL)
    // for employees who have just completed their first year.
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void checkAnnualLeaveEligibility() {
        int currentYear = LocalDate.now().getYear();
        List<User> allUsers = userRepository.findAll();
        List<LeavePolicy> policies = leavePolicyRepository.findAll();

        // Only process policies that require 1 year of service
        List<LeavePolicy> eligibilityPolicies = policies.stream()
                .filter(LeavePolicy::getRequiresOneYear)
                .collect(Collectors.toList());

        if (eligibilityPolicies.isEmpty()) return;

        for (User user : allUsers) {
            if (!hasCompletedOneYear(user)) continue;

            for (LeavePolicy policy : eligibilityPolicies) {
                boolean alreadyExists = leaveBalanceRepository
                        .findByUserIdAndLeaveTypeAndYear(user.getId(), policy.getLeaveType(), currentYear)
                        .isPresent();

                if (!alreadyExists) {
                    leaveBalanceRepository.save(LeaveBalance.builder()
                            .user(user)
                            .leaveType(policy.getLeaveType())
                            .year(currentYear)
                            .totalDays(policy.getTotalDaysPerYear())
                            .usedDays(0)
                            .pendingDays(0)
                            .carryForwardDays(0)
                            .build());
                    System.out.println("[LeavePolicyService] Created " + policy.getLeaveType()
                            + " balance for user " + user.getName() + " (ID: " + user.getId()
                            + ") — completed 1 year of service.");
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private boolean hasCompletedOneYear(User user) {
        if (user.getCreatedAt() == null) return false;
        return user.getCreatedAt().plusYears(1).isBefore(LocalDate.now().atStartOfDay())
                || user.getCreatedAt().plusYears(1).isEqual(LocalDate.now().atStartOfDay());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD for policies (admin-managed)
    // ─────────────────────────────────────────────────────────────────────────
    public List<LeavePolicyDto> getAllPolicies() {
        return leavePolicyRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public LeavePolicyDto getPolicyByType(String leaveType) {
        LeavePolicy policy = leavePolicyRepository.findByLeaveType(leaveType.toUpperCase())
                .orElseThrow(() -> new RuntimeException("No policy found for leave type: " + leaveType));
        return mapToDto(policy);
    }

    @Transactional
    public LeavePolicyDto updatePolicy(Long id, LeavePolicyDto dto) {
        LeavePolicy policy = leavePolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Policy not found with id: " + id));

        policy.setTotalDaysPerYear(dto.getTotalDaysPerYear());
        policy.setRequiresOneYear(dto.getRequiresOneYear());
        policy.setCarryForward(dto.getCarryForward());
        policy.setMaxCarryForwardDays(dto.getMaxCarryForwardDays());
        policy.setIsPublicHoliday(dto.getIsPublicHoliday());
        policy.setApplyBeforeDays(dto.getApplyBeforeDays());

        return mapToDto(leavePolicyRepository.save(policy));
    }

    private LeavePolicyDto mapToDto(LeavePolicy p) {
        return LeavePolicyDto.builder()
                .id(p.getId())
                .leaveType(p.getLeaveType())
                .totalDaysPerYear(p.getTotalDaysPerYear())
                .requiresOneYear(p.getRequiresOneYear())
                .carryForward(p.getCarryForward())
                .maxCarryForwardDays(p.getMaxCarryForwardDays())
                .isPublicHoliday(p.getIsPublicHoliday())
                .applyBeforeDays(p.getApplyBeforeDays())
                .build();
    }
}