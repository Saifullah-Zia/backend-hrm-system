package com.hrm.system.service;

import com.hrm.system.dto.LeaveBalanceDto;
import com.hrm.system.dto.LeaveBalanceUpdateRequest;
import com.hrm.system.model.LeaveBalance;
import com.hrm.system.model.User;
import com.hrm.system.repository.LeaveBalanceRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    // ─── Get all balances for a user in the current year (paginated) ──────────
    @Transactional(readOnly = true)
    public Page<LeaveBalanceDto> getBalancesForUser(Long userId, int page, int size) {
        int year = LocalDate.now().getYear();
        Pageable pageable = PageRequest.of(page, size, Sort.by("leaveType").ascending());
        return leaveBalanceRepository.findByUserIdAndYear(userId, year, pageable)
                .map(this::mapToDto);
    }

    // ─── Admin: get all balances for all users in current year (paginated) ────
    @Transactional(readOnly = true)
    public Page<LeaveBalanceDto> getAllBalancesCurrentYear(int page, int size) {
        int year = LocalDate.now().getYear();
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return leaveBalanceRepository.findByYear(year, pageable)
                .map(this::mapToDto);
    }

    // ─── Initialize leave balances for a user for a given year ───────────────
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

    // ─── Get a specific balance for user + leaveType + current year ───────────
    @Transactional(readOnly = true)
    public LeaveBalanceDto getBalance(Long userId, String leaveType) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndLeaveTypeAndYear(userId, leaveType.toUpperCase(), year)
                .orElseThrow(() -> new RuntimeException(
                        "No balance found for user " + userId + " and leave type " + leaveType));
        return mapToDto(balance);
    }

    // ─── Validation helper ────────────────────────────────────────────────────
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

    // ─── Reserve pending days on APPLY ────────────────────────────────────────
    @Transactional
    public void reservePendingDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.incrementPendingDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─── Convert pending → used on APPROVE ───────────────────────────────────
    @Transactional
    public void convertPendingToUsed(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementPendingDays(userId, leaveType.toUpperCase(), year, days);
        leaveBalanceRepository.incrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─── Release pending days on REJECT or CANCEL ─────────────────────────────
    @Transactional
    public void releasePendingDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementPendingDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─── Refund used days on APPROVED leave cancellation ─────────────────────
    @Transactional
    public void refundUsedDays(Long userId, String leaveType, int days) {
        int year = LocalDate.now().getYear();
        leaveBalanceRepository.decrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─── Direct deduction/refund for manual attendance ON_LEAVE ─────────────
    @Transactional
    public void deductDirectUsedDays(Long userId, String leaveType, int days, int year) {
        if (leaveType == null || leaveType.equalsIgnoreCase("UNPAID")) return;
        leaveBalanceRepository.incrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    @Transactional
    public void refundDirectUsedDays(Long userId, String leaveType, int days, int year) {
        if (leaveType == null || leaveType.equalsIgnoreCase("UNPAID")) return;
        leaveBalanceRepository.decrementUsedDays(userId, leaveType.toUpperCase(), year, days);
    }

    // ─── Admin: manually adjust a leave balance row ───────────────────────────
    @Transactional
    public LeaveBalanceDto updateBalance(Long id, LeaveBalanceUpdateRequest req) {
        LeaveBalance balance = leaveBalanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave balance not found: " + id));

        int oldTotal = balance.getTotalDays();
        int oldUsed = balance.getUsedDays();

        if (req.getTotalDays() != null) {
            int requestedTotal = req.getTotalDays();
            if (requestedTotal < 0) {
                throw new RuntimeException("Total days cannot be negative.");
            }

            int explicitUsed = req.getUsedDays() != null ? req.getUsedDays() : oldUsed;
            // When admin lowers total without changing used, treat the drop as consumed days
            // so employees see e.g. "1 used · 10 total" with 9 remaining (not "0 used · 9 total").
            if (requestedTotal < oldTotal && explicitUsed == oldUsed) {
                int delta = oldTotal - requestedTotal;
                balance.setUsedDays(oldUsed + delta);
                balance.setTotalDays(oldTotal);
            } else {
                balance.setTotalDays(requestedTotal);
                if (req.getUsedDays() != null) {
                    if (req.getUsedDays() < 0) {
                        throw new RuntimeException("Used days cannot be negative.");
                    }
                    balance.setUsedDays(req.getUsedDays());
                }
            }
        } else if (req.getUsedDays() != null) {
            if (req.getUsedDays() < 0) {
                throw new RuntimeException("Used days cannot be negative.");
            }
            balance.setUsedDays(req.getUsedDays());
        }

        if (req.getPendingDays() != null) {
            if (req.getPendingDays() < 0) {
                throw new RuntimeException("Pending days cannot be negative.");
            }
            balance.setPendingDays(req.getPendingDays());
        }
        if (req.getCarryForwardDays() != null) {
            if (req.getCarryForwardDays() < 0) {
                throw new RuntimeException("Carry-forward days cannot be negative.");
            }
            balance.setCarryForwardDays(req.getCarryForwardDays());
        }

        if (balance.getUsedDays() + balance.getPendingDays() > balance.getTotalDays()) {
            throw new RuntimeException(
                    "Used (" + balance.getUsedDays() + ") + pending (" + balance.getPendingDays() +
                            ") cannot exceed total (" + balance.getTotalDays() + ").");
        }

        return mapToDto(leaveBalanceRepository.save(balance));
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────
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