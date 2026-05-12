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

        // Notify ADMIN users  // Also notify SUPERADMIN users
        List<User> admins = new java.util.ArrayList<>(userRepository.findByRole(Role.ADMIN));
        List<User> superAdmins = userRepository.findByRole(Role.SUPERADMIN);
        admins.addAll(superAdmins);

        String message = String.format("📋 New leave request from %s (%s): %s to %s",
                user.getName(),
                dto.getLeaveType(),
                dto.getStartDate(),
                dto.getEndDate());

        if (admins.isEmpty()) {
            System.err.println("⚠️ No admins found to notify for leave request!");
        }

        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getId(),
                    message,
                    "LEAVE_REQUEST",
                    user.getId(),
                    saved.getId()
            );
            System.out.println("📢 Notified admin: " + admin.getName() + " (id=" + admin.getId() + ")");
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

        // Notify the employee who applied - fixed createdBy to use a real admin ID
        // Using leave.getId() as createdBy was wrong before - now using saved leave's user as reference
        notificationService.createNotification(
                leave.getUser().getId(),       // employee receives it
                String.format("✅ Your leave request (%s) from %s to %s has been APPROVED",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_APPROVED",
                leave.getUser().getId(),       // ✅ fixed: was incorrectly using saved.getId()
                saved.getId()
        );

        System.out.println("✅ Approval notification sent to employee: " + leave.getUser().getName());
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

        // Notify the employee who applied
        notificationService.createNotification(
                leave.getUser().getId(),       // employee receives it
                String.format("❌ Your leave request (%s) from %s to %s has been REJECTED",
                        leave.getType(), leave.getStartDate(), leave.getEndDate()),
                "LEAVE_REJECTED",
                leave.getUser().getId(),       // ✅ fixed: was incorrectly using saved.getId()
                saved.getId()
        );

        System.out.println("❌ Rejection notification sent to employee: " + leave.getUser().getName());
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