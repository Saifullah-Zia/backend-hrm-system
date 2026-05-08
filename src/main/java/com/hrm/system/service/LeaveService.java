package com.hrm.system.service;

import com.hrm.system.dto.LeaveDto;
import com.hrm.system.model.Leave;
import com.hrm.system.model.LeaveStatus;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.LeaveRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private NotificationService notificationService;

    // Apply for leave - sends notification to all admins
    public LeaveDto applyLeave(LeaveDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found on this ID: " + dto.getUserId()));

        Leave leave = new Leave();
        leave.setUser(user);
        leave.setType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setReason(dto.getReason());
        leave.setStatus(LeaveStatus.PENDING);

        Leave saved = leaveRepository.save(leave);

        // Send notification to all admins - FIXED: Use Role enum
        List<User> admins = userRepository.findByRoleIn(List.of(Role.ADMIN, Role.SUPER_ADMIN));

        String message = String.format("📋 New leave request from %s (%s): %s to %s",
                user.getName(),
                dto.getLeaveType(),
                dto.getStartDate(),
                dto.getEndDate());

        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getId(),
                    message,
                    "LEAVE_REQUEST",
                    user.getId(),
                    saved.getId()
            );
        }

        return mapToDto(saved);
    }

    // Get all leaves
    public List<LeaveDto> getAllLeaves() {
        return leaveRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // Get leave by ID
    public LeaveDto getLeaveById(Long id) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found on this ID: " + id));
        return mapToDto(leave);
    }

    // Get leave by user ID
    public List<LeaveDto> getLeaveByUserID(Long userId) {
        return leaveRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // Get leave by status
    public List<LeaveDto> getLeaveByStatus(String status) {
        LeaveStatus leaveStatus = LeaveStatus.valueOf(status.toUpperCase());
        List<Leave> results = leaveRepository.findByStatus(leaveStatus);
        return results.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // Approve leave
    public LeaveDto approveLeave(Long id) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found on this ID: " + id));

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Only PENDING leaves can be approved.");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        Leave saved = leaveRepository.save(leave);

        // Notify employee
        notificationService.createNotification(
                leave.getUser().getId(),
                String.format("✅ Your leave request (%s) from %s to %s has been APPROVED",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_APPROVED",
                saved.getId(),
                id
        );

        return mapToDto(saved);
    }

    // Reject leave
    public LeaveDto rejectLeave(Long id) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found on this ID: " + id));

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Only PENDING leaves can be rejected");
        }

        leave.setStatus(LeaveStatus.REJECT);
        Leave saved = leaveRepository.save(leave);

        // Notify employee
        notificationService.createNotification(
                leave.getUser().getId(),
                String.format("❌ Your leave request (%s) from %s to %s has been REJECTED",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_REJECTED",
                saved.getId(),
                id
        );

        return mapToDto(saved);
    }

    // Update leave if still pending
    public LeaveDto updateLeave(Long id, LeaveDto dto) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found on this ID: " + id));

        leave.setType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setReason(dto.getReason());
        leave.setStatus(LeaveStatus.PENDING);

        return mapToDto(leaveRepository.save(leave));
    }

    // Delete if still pending
    public void deleteLeave(Long id) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found on this ID: " + id));

        if (!leave.getStatus().equals(LeaveStatus.PENDING)) {
            throw new RuntimeException("Cannot delete a leave that is already " + leave.getStatus());
        }

        leaveRepository.deleteById(id);
    }

    // Map entity to DTO
    public LeaveDto mapToDto(Leave leave) {
        LeaveDto dto = new LeaveDto();
        dto.setId(leave.getId());
        dto.setUserId(leave.getUser().getId());
        dto.setUserName(leave.getUser().getName());
        dto.setLeaveType(leave.getType());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setReason(leave.getReason());
        dto.setStatus(String.valueOf(leave.getStatus()));
        return dto;
    }
}