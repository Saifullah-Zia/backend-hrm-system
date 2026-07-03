package com.hrm.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/hikvision")
public class HikvisionWebhookController {

    private final EmployeeProfileRepository employeeProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HikvisionWebhookController(EmployeeProfileRepository employeeProfileRepository,
                                      AttendanceRepository attendanceRepository,
                                      AttendanceService attendanceService) {
        this.employeeProfileRepository = employeeProfileRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestParam Map<String, String> formData,
                                          @RequestParam(required = false) MultipartFile file) {
        try {
            // Log all received form data for debugging
            System.out.println("=== HIKVISION WEBHOOK RECEIVED ===");
            System.out.println("Form data keys: " + formData.keySet());
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
            if (file != null) {
                System.out.println("File received: " + file.getOriginalFilename() + ", size: " + file.getSize());
            }
            System.out.println("===================================");

            // Hikvision sends event data as JSON string in event_log field
            String eventLogJson = formData.get("event_log");
            if (eventLogJson == null || eventLogJson.isBlank()) {
                return ResponseEntity.badRequest().body("event_log field is required");
            }

            // Parse the JSON structure
            JsonNode rootNode = objectMapper.readTree(eventLogJson);
            JsonNode accessControllerEvent = rootNode.path("AccessControllerEvent");

            // Extract employee ID from verifyNo field (this is the employee/person ID from the device)
            Integer employeeId = null;
            if (accessControllerEvent.has("verifyNo")) {
                employeeId = accessControllerEvent.get("verifyNo").asInt();
            }

            if (employeeId == null || employeeId <= 0) {
                return ResponseEntity.badRequest().body("Employee ID (verifyNo) not found in event data");
            }

            // Look up employee by biometricPersonId
            EmployeeProfile profile = employeeProfileRepository.findByBiometricPersonId(employeeId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No employee found with biometric Person ID: " + employeeId));

            Long userId = profile.getUser().getId();

            // Parse event timestamp to determine date
            String dateTime = rootNode.has("dateTime") ? rootNode.get("dateTime").asText() : null;
            LocalDate eventDate = parseEventDate(dateTime);

            // Check if already checked in today
            boolean alreadyCheckedIn = attendanceRepository
                    .findByUserIdAndDate(userId, eventDate)
                    .isPresent();

            if (alreadyCheckedIn) {
                // Check out
                attendanceService.checkOut(userId);
                return ResponseEntity.ok("Check-out recorded for employee ID: " + employeeId);
            } else {
                // Check in
                attendanceService.checkIn(userId);
                return ResponseEntity.ok("Check-in recorded for employee ID: " + employeeId);
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
            // Try ISO 8601 format first (Hikvision format: 2024-09-05T17:47:39+08:00)
            LocalDateTime eventDateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
            return eventDateTime.toLocalDate();
        } catch (Exception e) {
            try {
                // Fallback to custom format
                LocalDateTime eventDateTime = LocalDateTime.parse(timestamp, EVENT_TIME_FORMATTER);
                return eventDateTime.toLocalDate();
            } catch (Exception e2) {
                // If parsing fails, use today's date
                return LocalDate.now();
            }
        }
    }
}
