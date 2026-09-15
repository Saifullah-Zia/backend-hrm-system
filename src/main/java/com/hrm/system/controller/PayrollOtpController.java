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

    @Autowired
    private com.hrm.system.repository.UserRepository userRepository;

    private Long getUserIdFromRequest(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return (Long) userId;
        }

        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                String username = null;
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    username = userDetails.getUsername();
                } else if (principal instanceof String str) {
                    username = str;
                }

                if (username != null) {
                    java.util.Optional<com.hrm.system.model.User> u = userRepository.findByEmail(username);
                    if (u.isEmpty()) {
                        u = userRepository.findByName(username);
                    }
                    if (u.isPresent()) {
                        return u.get().getId();
                    }
                }
            }
        } catch (Exception ignored) {}

        throw new RuntimeException("Unauthorized user context");
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
