package com.hrm.system.controller;

import com.hrm.system.dto.HikvisionEventDto;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/hikvision")
public class HikvisionWebhookController {

    private final EmployeeProfileRepository employeeProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HikvisionWebhookController(EmployeeProfileRepository employeeProfileRepository,
                                      AttendanceRepository attendanceRepository,
                                      AttendanceService attendanceService) {
        this.employeeProfileRepository = employeeProfileRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody HikvisionEventDto event) {
        try {
            // Validate required fields
            if (event.getEmployeeId() == null) {
                return ResponseEntity.badRequest().body("Employee ID is required");
            }

            // Look up employee by biometricPersonId
            EmployeeProfile profile = employeeProfileRepository.findByBiometricPersonId(event.getEmployeeId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No employee found with biometric Person ID: " + event.getEmployeeId()));

            Long userId = profile.getUser().getId();

            // Parse event timestamp to determine date
            LocalDate eventDate = parseEventDate(event.getTimestamp());

            // Check if already checked in today
            boolean alreadyCheckedIn = attendanceRepository
                    .findByUserIdAndDate(userId, eventDate)
                    .isPresent();

            if (alreadyCheckedIn) {
                // Check out
                attendanceService.checkOut(userId);
                return ResponseEntity.ok("Check-out recorded for employee ID: " + event.getEmployeeId());
            } else {
                // Check in
                attendanceService.checkIn(userId);
                return ResponseEntity.ok("Check-in recorded for employee ID: " + event.getEmployeeId());
            }

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }

    private LocalDate parseEventDate(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDate.now();
        }
        try {
            LocalDateTime eventDateTime = LocalDateTime.parse(timestamp, EVENT_TIME_FORMATTER);
            return eventDateTime.toLocalDate();
        } catch (Exception e) {
            // If parsing fails, use today's date
            return LocalDate.now();
        }
    }
}
