package com.hrm.system.controller;

import com.hrm.system.dto.EmployeeProfileDto;
import com.hrm.system.service.EmployeeProfileService;
import com.hrm.system.service.SalaryOtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/employee-profiles")
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final EmployeeProfileService employeeProfileService;
    private final SalaryOtpService salaryOtpService;

    @GetMapping
    public ResponseEntity<List<EmployeeProfileDto>> getAll() {
        return ResponseEntity.ok(employeeProfileService.getAll());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<EmployeeProfileDto>> getPaged(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(defaultValue = "firstName") String sortBy,
            @RequestParam(defaultValue = "asc")  String sortDir,
            @RequestParam(required = false)      String search,
            @RequestParam(required = false)      Long departmentId) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(
                employeeProfileService.getPaged(search, departmentId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeProfileDto> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(employeeProfileService.getById(id));
    }

    // get profile by user id
    @GetMapping("/user/{userId}")
    public ResponseEntity<EmployeeProfileDto> getByUserId(
            @PathVariable Long userId) {
        return ResponseEntity.ok(employeeProfileService.getByUserId(userId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<EmployeeProfileDto> create(
            @Valid @RequestBody EmployeeProfileDto dto) {
        return ResponseEntity.ok(employeeProfileService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<EmployeeProfileDto> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeProfileDto dto) {
        return ResponseEntity.ok(employeeProfileService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        employeeProfileService.delete(id);
        return ResponseEntity.ok("Employee profile deleted successfully");
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public ResponseEntity<EmployeeProfileDto> getMyProfile() {
        return ResponseEntity.ok(employeeProfileService.getMe());
    }

    // ── Salary OTP endpoints ──────────────────────────────────────────────────

    /**
     * Sends a 6-digit OTP to the currently logged-in admin's email.
     * The admin must be ADMIN or SUPERADMIN.
     */
    @PostMapping("/salary-otp/request")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, String>> requestSalaryOtp(HttpServletRequest request) {
        Long adminUserId = (Long) request.getAttribute("userId");
        if (adminUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: user ID not found in token."));
        }
        try {
            salaryOtpService.generateAndSend(adminUserId);
            return ResponseEntity.ok(Map.of("message", "Verification code sent to your registered email."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Verifies the OTP code submitted by the admin.
     * Returns { "valid": true } or { "valid": false, "error": "..." }.
     */
    @PostMapping("/salary-otp/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> verifySalaryOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long adminUserId = (Long) request.getAttribute("userId");
        if (adminUserId == null) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "error", "Unauthorized."));
        }
        String code = body.get("code");
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Code is required."));
        }
        boolean valid = salaryOtpService.verify(adminUserId, code);
        if (valid) {
            return ResponseEntity.ok(Map.of("valid", true));
        } else {
            return ResponseEntity.ok(Map.of("valid", false, "error", "Invalid or expired code. Please try again."));
        }
    }
}