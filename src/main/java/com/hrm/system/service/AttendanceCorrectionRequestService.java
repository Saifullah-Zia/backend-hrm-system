package com.hrm.system.service;

import com.hrm.system.dto.AttendanceCorrectionRequestDto;
import com.hrm.system.model.*;
import com.hrm.system.repository.AttendanceCorrectionRequestRepository;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceCorrectionRequestService {

    @Autowired
    private AttendanceCorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficeHoursService officeHoursService;

    @Autowired
    private NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<AttendanceCorrectionRequestDto> getRequestsByUser(Long userId) {
        return correctionRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceCorrectionRequestDto> getPendingRequests() {
        return correctionRequestRepository.findByStatusOrderByCreatedAtDesc(CorrectionStatus.PENDING)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttendanceCorrectionRequestDto getRequestById(Long id) {
        return correctionRequestRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new EntityNotFoundException("Correction request not found: " + id));
    }

    @Transactional
    public AttendanceCorrectionRequestDto submitRequest(AttendanceCorrectionRequestDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + dto.getUserId()));

        // Check if there is already a pending request for the same date
        boolean hasPending = correctionRequestRepository.findByUserIdAndDate(user.getId(), dto.getDate())
                .stream()
                .anyMatch(r -> r.getStatus() == CorrectionStatus.PENDING);
        if (hasPending) {
            throw new IllegalStateException("You already have a pending correction request for " + dto.getDate());
        }

        // Check if there is an existing attendance record for the date
        Attendance attendance = attendanceRepository.findByUserIdAndDate(user.getId(), dto.getDate()).orElse(null);

        AttendanceCorrectionRequest request = AttendanceCorrectionRequest.builder()
                .user(user)
                .attendance(attendance)
                .date(dto.getDate())
                .requestedCheckIn(dto.getRequestedCheckIn())
                .requestedCheckOut(dto.getRequestedCheckOut())
                .reason(dto.getReason())
                .status(CorrectionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        AttendanceCorrectionRequest saved = correctionRequestRepository.save(request);

        // Notify admins and superadmins
        List<User> admins = new ArrayList<>(userRepository.findByRole(Role.ADMIN));
        admins.addAll(userRepository.findByRole(Role.SUPERADMIN));

        String message = String.format("⏰ New attendance correction request from %s for %s", user.getName(), dto.getDate());
        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getId(),
                    message,
                    "ATTENDANCE_CORRECTION_REQUEST",
                    user.getId(),
                    saved.getId()
            );
        }

        return mapToDto(saved);
    }

    @Transactional
    public AttendanceCorrectionRequestDto approveRequest(Long id) {
        AttendanceCorrectionRequest request = correctionRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Correction request not found: " + id));

        if (request.getStatus() != CorrectionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved.");
        }

        request.setStatus(CorrectionStatus.APPROVED);
        AttendanceCorrectionRequest savedRequest = correctionRequestRepository.save(request);

        // Fetch or create the daily attendance record
        Attendance attendance = request.getAttendance();
        if (attendance == null) {
            attendance = attendanceRepository.findByUserIdAndDate(request.getUser().getId(), request.getDate()).orElse(null);
        }

        if (attendance == null) {
            attendance = new Attendance();
            attendance.setUser(request.getUser());
            attendance.setDate(request.getDate());
        }

        if (request.getRequestedCheckIn() != null) {
            attendance.setCheckIn(request.getRequestedCheckIn());
            attendance.setStatus(officeHoursService.calculateStatus(request.getRequestedCheckIn().toLocalTime()));
        } else {
            attendance.setStatus("PRESENT");
        }

        if (request.getRequestedCheckOut() != null) {
            attendance.setCheckOut(request.getRequestedCheckOut());
        }

        attendanceRepository.save(attendance);

        // Notify employee
        notificationService.createNotification(
                request.getUser().getId(),
                String.format("✅ Your attendance correction request for %s has been APPROVED.", request.getDate()),
                "ATTENDANCE_CORRECTION_APPROVED",
                request.getUser().getId(),
                savedRequest.getId()
        );

        return mapToDto(savedRequest);
    }

    @Transactional
    public AttendanceCorrectionRequestDto rejectRequest(Long id) {
        AttendanceCorrectionRequest request = correctionRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Correction request not found: " + id));

        if (request.getStatus() != CorrectionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected.");
        }

        request.setStatus(CorrectionStatus.REJECTED);
        AttendanceCorrectionRequest saved = correctionRequestRepository.save(request);

        // Notify employee
        notificationService.createNotification(
                request.getUser().getId(),
                String.format("❌ Your attendance correction request for %s has been REJECTED.", request.getDate()),
                "ATTENDANCE_CORRECTION_REJECTED",
                request.getUser().getId(),
                saved.getId()
        );

        return mapToDto(saved);
    }

    private AttendanceCorrectionRequestDto mapToDto(AttendanceCorrectionRequest entity) {
        return AttendanceCorrectionRequestDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .userName(entity.getUser().getName())
                .attendanceId(entity.getAttendance() != null ? entity.getAttendance().getId() : null)
                .date(entity.getDate())
                .requestedCheckIn(entity.getRequestedCheckIn())
                .requestedCheckOut(entity.getRequestedCheckOut())
                .reason(entity.getReason())
                .status(String.valueOf(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
