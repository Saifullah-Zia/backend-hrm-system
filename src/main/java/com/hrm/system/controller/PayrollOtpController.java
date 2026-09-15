package com.hrm.system.controller;

import com.hrm.system.service.PayrollOtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/payroll-otp")
public class PayrollOtpController {

    @Autowired
    private PayrollOtpService payrollOtpService;

    private Long getUserIdFromRequest(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized user context");
        }
        return (Long) userId;
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendPayrollOtp(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        Map<String, Object> result = payrollOtpService.sendPayrollOtp(userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verifyPayrollOtp(
            HttpServletRequest request,
            @RequestBody Map<String, String> payload) {
        Long userId = getUserIdFromRequest(request);
        String code = payload.get("code");
        Map<String, Object> result = payrollOtpService.verifyPayrollOtp(userId, code);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/check-elevated")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> checkElevatedStatus(
            HttpServletRequest request,
            @RequestHeader(value = "X-Payroll-Elevated-Token", required = false) String token) {
        Long userId = getUserIdFromRequest(request);
        boolean valid = payrollOtpService.isElevatedAccessValid(userId, token);
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}
