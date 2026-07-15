package com.hrm.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.service.AttendanceService;
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
                // Nothing to process - acknowledge so the device doesn't retry.
                return ResponseEntity.ok("Ignored: no event_log field present");
            }

            // Parse the JSON structure
            JsonNode rootNode = objectMapper.readTree(eventLogJson);
            JsonNode accessControllerEvent = rootNode.path("AccessControllerEvent");

            // Ignore events that are not successful verifications (e.g. failed/invalid scans,
            // door events, tamper alarms, etc). Returning 200 here (instead of an error) tells
            // the device the event was handled, so it stops endlessly retrying events we don't
            // care about. This is what was causing the same old failed event to be resent
            // forever - the controller was returning 404/400 for it, so the device kept retrying.
            String currentVerifyMode = accessControllerEvent.has("currentVerifyMode")
                    ? accessControllerEvent.get("currentVerifyMode").asText()
                    : null;
            if ("invalid".equalsIgnoreCase(currentVerifyMode)) {
                System.out.println("Ignoring invalid/failed verification event");
                return ResponseEntity.ok("Ignored: invalid verification event");
            }

            // Extract employee ID. On a SUCCESSFUL verification, Hikvision access controllers
            // send the real employee number in "employeeNoString". "verifyNo" is not the
            // employee ID - it's an internal counter and should only be used as a last resort
            // fallback, if at all.
            Integer employeeId = null;
            if (accessControllerEvent.has("employeeNoString")
                    && !accessControllerEvent.get("employeeNoString").asText().isBlank()) {
                try {
                    employeeId = Integer.parseInt(accessControllerEvent.get("employeeNoString").asText().trim());
                } catch (NumberFormatException ignored) {
                    // Not numeric - leave employeeId null and fall through below.
                }
            }
            if (employeeId == null && accessControllerEvent.has("verifyNo")) {
                employeeId = accessControllerEvent.get("verifyNo").asInt();
            }

            if (employeeId == null || employeeId <= 0) {
                // Acknowledge with 200 so the device does not keep retrying an event we can
                // never resolve into an employee.
                System.out.println("No usable employee ID field found in event, ignoring event");
                return ResponseEntity.ok("Ignored: no employee ID in event data");
            }

            // Create final variable for lambda
            final Integer finalEmployeeId = employeeId;

            // Look up employee by biometricPersonId
            EmployeeProfile profile = employeeProfileRepository.findByBiometricPersonId(finalEmployeeId)
                    .orElse(null);

            if (profile == null) {
                // Acknowledge with 200 (not 404) so the device doesn't keep retrying this
                // event forever just because we haven't mapped this person yet.
                System.out.println("No employee found with biometric Person ID: " + finalEmployeeId);
                return ResponseEntity.ok(
                        "Ignored: no employee found with biometric Person ID " + finalEmployeeId);
            }

            Long userId = profile.getUser().getId();

            // Parse event timestamp to determine date
            String dateTime = rootNode.has("dateTime") ? rootNode.get("dateTime").asText() : null;
            LocalDate eventDate = parseEventDate(dateTime);
            System.out.println("Processing attendance for employee ID: " + employeeId + ", user ID: " + userId + ", event date: " + eventDate + ", device timestamp: " + dateTime);

            // Check if already checked in today
            boolean alreadyCheckedIn = attendanceRepository
                    .findByUserIdAndDate(userId, eventDate)
                    .isPresent();
            System.out.println("Already checked in today: " + alreadyCheckedIn);

            if (alreadyCheckedIn) {
                // Check out — use the biometric-safe path, which does NOT enforce
                // webCheckInAllowed. Biometric employees have that flag set to
                // false, so calling the web checkOut() here would throw and the
                // checkout would silently never be persisted.
                try {
                    attendanceService.biometricCheckOut(userId);
                    return ResponseEntity.ok("Check-out recorded for employee ID: " + employeeId);
                } catch (IllegalStateException e) {
                    // Already checked out earlier today (e.g. 3rd+ scan of the day) - not an error,
                    // just acknowledge and do nothing.
                    System.out.println("Ignoring duplicate scan: " + e.getMessage());
                    return ResponseEntity.ok("Ignored: employee already checked out today");
                }
            } else {
                // Check in — use the biometric-safe path for the same reason as above.
                try {
                    attendanceService.biometricCheckIn(userId);
                    return ResponseEntity.ok("Check-in recorded for employee ID: " + employeeId);
                } catch (IllegalStateException e) {
                    // If check-in fails due to pending check-out from previous day, check out first then check in
                    if (e.getMessage().contains("Please check out from your previous shift first")) {
                        System.out.println("Pending check-out from previous day, checking out first then checking in");
                        try {
                            attendanceService.biometricCheckOut(userId);
                            attendanceService.biometricCheckIn(userId);
                            return ResponseEntity.ok("Check-out then check-in recorded for employee ID: " + employeeId);
                        } catch (IllegalStateException e2) {
                            System.out.println("Ignoring duplicate scan after auto check-out: " + e2.getMessage());
                            return ResponseEntity.ok("Ignored: employee already checked in today");
                        }
                    }
                    System.out.println("Ignoring duplicate scan: " + e.getMessage());
                    return ResponseEntity.ok("Ignored: employee already checked in today");
                }
            }

        } catch (Exception e) {
            // Still return 200 for unexpected errors related to event content, to avoid
            // infinite device retries. Log the error so it's visible in Railway logs.
            System.out.println("Error processing webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("Acknowledged (error logged): " + e.getMessage());
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