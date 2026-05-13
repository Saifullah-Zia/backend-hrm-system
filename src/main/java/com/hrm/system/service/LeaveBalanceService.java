package com.hrm.system.service;

import com.hrm.system.dto.LeaveBalanceDto;
import com.hrm.system.model.LeaveBalance;
import com.hrm.system.model.User;
import com.hrm.system.repository.LeaveBalanceRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaveBalanceService {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Get all balances for a user in the current year
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getBalancesForUser(Long userId) {
        int year = LocalDate.now().getYear();
        return leaveBalanceRepository.findByUserIdAndYear(userId, year)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
// Initialize leave balances for a user for a given year
// ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void initializeBalancesForUser(User user, int year) {
        Map<String, Integer> defaultDays = Map.of(
                "SICK",       10,
                "CASUAL",     12,
                "EIDULFITAR",  3,
                "EIDULAZHA",   3
        );

        for (Map.Entry<String, Integer> entry : defaultDays.entrySet()) {
            boolean exists = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeAndYear(user.getId(), entry.getKey(), year)
                    .isPresent();

            if (!exists) {
                LeaveBalance balance = LeaveBalance.builder()
                        .user(user)
                        .leaveType(entry.getKey())
                        .year(year)
                        .totalDays(entry.getValue())
                        .usedDays(0)
                        .pendingDays(0)
                        .carryForwardDays(0)
                        .build();
                leaveBalanceRepository.save(balance);
            }
        }
    }

    // Get a specific balance for user + leaveType + current year
    @Transactional(readOnly = true)
    public LeaveBalanceDto getBalance(Long userId, String leaveType) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndLeaveTypeAndYear(userId, leaveType.toUpperCase(), year)
                .orElseThrow(() -> new RuntimeException(
                        "No balance found for user " + userId + " and leave type " + leaveType));
        return mapToDto(balance);
    }

    // Admin: get all balances for all users in current year
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getAllBalancesCurrentYear() {
        int year = LocalDate.now().getYear();
        return leaveBalanceRepository.findByYear(year)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation helper — used by LeaveService before allowing an application
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public void validateSufficientBalance(Long userId, String leaveType, int requestedDays) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndLeaveTypeAndYear(userId, leaveType.toUpperCase(), year)
                .orElseThrow(() -> new RuntimeException(
                        "You are not eligible for " + leaveType + " leave this year."));

        int remaining = balance.getRemainingDays();

        if (remaining <= 0) {
            throw new RuntimeException(
                    "You have no remaining " + leaveType + " leaves for " + year +
                            ". Your quota of " + balance.getTotalDays() + " days has been exhausted.");
        }

        if (requestedDays > remaining) {
            throw new RuntimeException(
                    "Insufficient " + leaveType + " leave balance. " +
                            "You requested " + requestedDays + " day(s) but only " +
                            remaining + " day(s) remain for " + year + ".");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Called by LeaveService on APPLY → reserve as pending
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void reservePendingDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.incrementPendingDays(userId, leaveType.toUpperCase(), year, days);
    }

    // Called on APPROVE → convert pending → used
    @Transactional
    public void convertPendingToUsed(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementPendingDays(userId, leaveType.toUpperCase(), year, days);
        leaveBalanceRepository.incrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    // Called on REJECT or CANCEL → release pending days back
    @Transactional
    public void releasePendingDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementPendingDays(userId, leaveType.toUpperCase(), year, days);
    }

    // Called when an APPROVED leave is cancelled → refund used days
    @Transactional
    public void refundUsedDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────────────────────
    public LeaveBalanceDto mapToDto(LeaveBalance lb) {
        return LeaveBalanceDto.builder()
                .id(lb.getId())
                .userId(lb.getUser().getId())
                .userName(lb.getUser().getName())
                .leaveType(lb.getLeaveType())
                .year(lb.getYear())
                .totalDays(lb.getTotalDays())
                .usedDays(lb.getUsedDays())
                .pendingDays(lb.getPendingDays())
                .remainingDays(lb.getRemainingDays())
                .carryForwardDays(lb.getCarryForwardDays())
                .build();
    }
}