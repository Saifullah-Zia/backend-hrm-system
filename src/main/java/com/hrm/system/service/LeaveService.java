package com.hrm.system.service;

import com.hrm.system.dto.LeaveDto;
import com.hrm.system.model.*;
import com.hrm.system.repository.LeaveRepository;
import com.hrm.system.repository.LeavePolicyRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LeaveEligibilityService leaveEligibilityService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AttendanceService attendanceService;

    // ─────────────────────────────────────────────────────────────────────────
    // Apply for leave
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public LeaveDto applyLeave(LeaveDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + dto.getUserId()));

        String leaveType = dto.getLeaveType().toUpperCase();

        // ── 1. Load policy ───────────────────────────────────────────────────
        // UNPAID leave doesn't require a policy
        LeavePolicy policy = null;
        if (!leaveType.equals("UNPAID")) {
            policy = leavePolicyRepository.findByLeaveType(leaveType)
                    .orElseThrow(() -> new RuntimeException("Unknown leave type: " + leaveType));
        }

        // ── 2. Date validation ───────────────────────────────────────────────
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new RuntimeException("End date cannot be before start date.");
        }

        // ── 3. Eid leave: can only apply within [eventDate - applyBeforeDays, eventDate+duration] ─
        if (policy != null && policy.getIsPublicHoliday() && policy.getApplyBeforeDays() != null) {
            LocalDate today = LocalDate.now();
            LocalDate earliestApplyDate = dto.getStartDate().minusDays(policy.getApplyBeforeDays());
            if (today.isBefore(earliestApplyDate)) {
                throw new RuntimeException(
                        "You can only apply for " + leaveType + " leave up to " +
                                policy.getApplyBeforeDays() + " day(s) before the leave start date. " +
                                "Earliest application date: " + earliestApplyDate);
            }
        }

        // ── 4. Annual leave: requires 1 year of service from joining date ──────
        if (policy != null && policy.getRequiresOneYear()) {
            if (!leaveEligibilityService.hasCompletedOneYear(user)) {
                throw new RuntimeException(
                        "You are not eligible for Annual Leave yet. " +
                                "Annual leave is available after completing 1 year of service from your joining date.");
            }
        }

        // 5. Probation check: only UNPAID leave allowed
        if (leaveEligibilityService.isOnProbation(user)) {
            if (!leaveType.equals("UNPAID")) {
                throw new RuntimeException(
                        "You are on probation. You can only apply for Unpaid Leave.");
            }
        }

        // ── 6. Calculate duration
        int duration = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        // ── 7. Balance check — throws descriptive error if insufficient ───────
        // Skip balance check for UNPAID leave
        if (!leaveType.equals("UNPAID")) {
            leaveBalanceService.validateSufficientBalance(user.getId(), leaveType, duration);
        }

        // ── 8. Persist leave 
        Leave leave = Leave.builder()
                .user(user)
                .type(leaveType)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .durationDays(duration)
                .attachmentUrl(dto.getAttachmentUrl())
                .build();

        Leave saved = leaveRepository.save(leave);

        // ── 9. Reserve balance as pending ─────────────────────────────────────
        // Skip balance reservation for UNPAID leave
        if (!leaveType.equals("UNPAID")) {
            leaveBalanceService.reservePendingDays(user.getId(), leaveType, duration);
        }

        // ── 10. Notify all admins & superadmins ───────────────────────────────
        List<User> admins = new ArrayList<>(userRepository.findByRole(Role.ADMIN));
        admins.addAll(userRepository.findByRole(Role.SUPERADMIN));

        String message = String.format(
                "📋 New leave request from %s (%s): %s to %s (%d day(s))",
                user.getName(), leaveType, dto.getStartDate(), dto.getEndDate(), duration);

        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getId(), message, "LEAVE_REQUEST", user.getId(), saved.getId());

            // Send email notification
            emailService.sendLeaveRequestNotification(
                    admin.getEmail(),
                    user.getName(),
                    leaveType,
                    dto.getStartDate().toString(),
                    dto.getEndDate().toString(),
                    duration,
                    dto.getReason()
            );
        }

        // ── 10. Build response with updated remaining balance ─────────────────
        int remaining = leaveBalanceService
                .getBalance(user.getId(), leaveType)
                .getRemainingDays();

        LeaveDto response = mapToDto(saved);
        response.setRemainingDaysAfterRequest(remaining);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Approve
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public LeaveDto approveLeave(Long id) {
        Leave leave = findLeaveOrThrow(id);

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Only PENDING leaves can be approved.");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        Leave saved = leaveRepository.save(leave);

        // Move from pending → used in balance
        leaveBalanceService.convertPendingToUsed(
                leave.getUser().getId(), leave.getType(), leave.getDurationDays());

        // Mark attendance as ON_LEAVE or UNPAID_LEAVE for every day in the approved range
        LocalDate date = leave.getStartDate();
        while (!date.isAfter(leave.getEndDate())) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            // Skip weekends (Saturday, Sunday)
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                String attendanceStatus = leave.getType().equals("UNPAID") ? "UNPAID_LEAVE" : "ON_LEAVE";
                attendanceService.createOrUpdateAttendanceForLeave(
                        leave.getUser().getId(), date, attendanceStatus);
            }
            date = date.plusDays(1);
        }

        notificationService.createNotification(
                leave.getUser().getId(),
                String.format("✅ Your %s leave request (%s to %s) has been APPROVED.",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_APPROVED", leave.getUser().getId(), saved.getId());

        // Send email notification to employee
        // Wrapped so an email failure doesn't roll back the approval itself
        try {
            emailService.sendLeaveApprovedNotification(
                    leave.getUser().getEmail(),
                    leave.getUser().getName(),
                    leave.getType(),
                    leave.getStartDate().toString(),
                    leave.getEndDate().toString(),
                    leave.getDurationDays()
            );
        } catch (Exception e) {
            System.err.println("✗ Failed to send leave approved email for leave id: " + saved.getId());
            e.printStackTrace();
        }

        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reject
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public LeaveDto rejectLeave(Long id) {
        Leave leave = findLeaveOrThrow(id);

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Only PENDING leaves can be rejected.");
        }

        leave.setStatus(LeaveStatus.REJECT);
        Leave saved = leaveRepository.save(leave);

        // Release pending days back to available
        leaveBalanceService.releasePendingDays(
                leave.getUser().getId(), leave.getType(), leave.getDurationDays());

        notificationService.createNotification(
                leave.getUser().getId(),
                String.format("❌ Your %s leave request (%s to %s) has been REJECTED.",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_REJECTED", leave.getUser().getId(), saved.getId());

        // Send email notification to employee
        // Wrapped so an email failure doesn't roll back the rejection itself
        try {
            emailService.sendLeaveRejectedNotification(
                    leave.getUser().getEmail(),
                    leave.getUser().getName(),
                    leave.getType(),
                    leave.getStartDate().toString(),
                    leave.getEndDate().toString(),
                    leave.getDurationDays()
            );
        } catch (Exception e) {
            System.err.println("✗ Failed to send leave rejected email for leave id: " + saved.getId());
            e.printStackTrace();
        }

        return mapToDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update (only PENDING leaves)
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public LeaveDto updateLeave(Long id, LeaveDto dto) {
        Leave leave = findLeaveOrThrow(id);

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Only PENDING leaves can be updated.");
        }

        String leaveType = dto.getLeaveType().toUpperCase();
        int newDuration = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        int oldDuration = leave.getDurationDays();

        // Release old pending days, then validate + reserve new amount
        leaveBalanceService.releasePendingDays(leave.getUser().getId(), leave.getType(), oldDuration);
        leaveBalanceService.validateSufficientBalance(leave.getUser().getId(), leaveType, newDuration);
        leaveBalanceService.reservePendingDays(leave.getUser().getId(), leaveType, newDuration);

        leave.setType(leaveType);
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setReason(dto.getReason());
        leave.setDurationDays(newDuration);

        return mapToDto(leaveRepository.save(leave));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete (only PENDING)
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void deleteLeave(Long id) {
        Leave leave = findLeaveOrThrow(id);

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException(
                    "Cannot delete a leave that is already " + leave.getStatus() + ". " +
                            "Only PENDING leaves can be withdrawn.");
        }

        leaveBalanceService.releasePendingDays(
                leave.getUser().getId(), leave.getType(), leave.getDurationDays());

        leaveRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read operations
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LeaveDto> getAllLeaves() {
        return leaveRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveDto getLeaveById(Long id) {
        return mapToDto(findLeaveOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> getLeaveByUserID(Long userId) {
        return leaveRepository.findByUserId(userId).stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> getLeaveByStatus(String status) {
        LeaveStatus leaveStatus = LeaveStatus.valueOf(status.toUpperCase());
        return leaveRepository.findByStatus(leaveStatus).stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<LeaveDto> getPagedRequests(String statusStr, Pageable pageable) {
        LeaveStatus status = (statusStr != null) ? LeaveStatus.valueOf(statusStr.toUpperCase()) : null;
        return leaveRepository
                .findAllPaged(status, pageable)
                .map(this::mapToDto);   // replace toDto() with your existing mapper
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private Leave findLeaveOrThrow(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found with id: " + id));
    }

    public LeaveDto mapToDto(Leave leave) {
        return LeaveDto.builder()
                .id(leave.getId())
                .userId(leave.getUser().getId())
                .userName(leave.getUser().getName())
                .leaveType(leave.getType())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .reason(leave.getReason())
                .status(String.valueOf(leave.getStatus()))
                .durationDays(leave.getDurationDays())
                .attachmentUrl(leave.getAttachmentUrl())
                .build();
    }
}