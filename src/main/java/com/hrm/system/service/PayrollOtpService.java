package com.hrm.system.service;

import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PayrollOtpService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogService auditLogService;

    @Data
    public static class OtpState {
        private String code;
        private LocalDateTime expiry;
        private int attempts = 0;
        private LocalDateTime lockoutUntil;
        private List<LocalDateTime> sendTimestamps = new ArrayList<>();
        private String elevatedToken;
        private LocalDateTime elevatedExpiry;
    }

    private final Map<Long, OtpState> userOtpMap = new ConcurrentHashMap<>();

    // Mask email e.g. "m***@company.com"
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****@****.com";
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }

    public Map<String, Object> sendPayrollOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new RuntimeException("No registered email address found for your account.");
        }

        OtpState state = userOtpMap.computeIfAbsent(userId, k -> new OtpState());
        LocalDateTime now = LocalDateTime.now();

        // 1. Check if user is locked out from too many failed attempts
        if (state.getLockoutUntil() != null && now.isBefore(state.getLockoutUntil())) {
            long remainingMins = java.time.Duration.between(now, state.getLockoutUntil()).toMinutes() + 1;
            throw new RuntimeException(String.format("Too many failed attempts. Payroll access locked for %d minute(s).", remainingMins));
        }

        // 2. Rate limiting check: max 3 sends per 10 minutes
        state.getSendTimestamps().removeIf(t -> t.isBefore(now.minusMinutes(10)));
        if (state.getSendTimestamps().size() >= 3) {
            throw new RuntimeException("Too many OTP requests. Please wait a few minutes before requesting another code.");
        }

        // 3. Generate 6-digit OTP code
        String code = String.format("%06d", new Random().nextInt(1000000));
        state.setCode(code);
        state.setExpiry(now.plusMinutes(5));
        state.setAttempts(0); // reset attempts on new code send
        state.getSendTimestamps().add(now);

        // 4. Send email
        emailService.sendPayrollAccessOtpEmail(user.getEmail(), user.getName(), code);

        // 5. Audit trail
        String maskedEmail = maskEmail(user.getEmail());
        auditLogService.log("PayrollSecurity", userId, AuditAction.UPDATE,
                String.format("Payroll access OTP requested and sent to %s", maskedEmail), userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Verification code sent to your email.");
        response.put("maskedEmail", maskedEmail);
        response.put("expiresInMinutes", 5);
        return response;
    }

    public Map<String, Object> verifyPayrollOtp(Long userId, String codeInput) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        OtpState state = userOtpMap.get(userId);
        LocalDateTime now = LocalDateTime.now();

        if (state == null || state.getCode() == null || state.getExpiry() == null) {
            throw new RuntimeException("No verification code found. Please click 'Resend Code'.");
        }

        // Check lockout
        if (state.getLockoutUntil() != null && now.isBefore(state.getLockoutUntil())) {
            long remainingMins = java.time.Duration.between(now, state.getLockoutUntil()).toMinutes() + 1;
            throw new RuntimeException(String.format("Too many failed attempts. Payroll access locked for %d minute(s).", remainingMins));
        }

        // Check expiry
        if (now.isAfter(state.getExpiry())) {
            throw new RuntimeException("Verification code has expired. Please click 'Resend Code'.");
        }

        String cleanInput = codeInput != null ? codeInput.trim() : "";
        if (!state.getCode().equals(cleanInput)) {
            state.setAttempts(state.getAttempts() + 1);
            int remainingAttempts = 5 - state.getAttempts();

            auditLogService.log("PayrollSecurity", userId, AuditAction.UPDATE,
                    String.format("Failed payroll access OTP verification attempt (%d/5)", state.getAttempts()), userId);

            if (remainingAttempts <= 0) {
                state.setLockoutUntil(now.plusMinutes(15));
                state.setCode(null); // invalidate code
                throw new RuntimeException("Too many failed attempts. Payroll access locked for 15 minutes.");
            }

            throw new RuntimeException(String.format("Invalid verification code. %d attempt(s) remaining.", remainingAttempts));
        }

        // Success: clear OTP code, issue short-lived elevated token (30 min TTL)
        String elevatedToken = UUID.randomUUID().toString();
        state.setCode(null);
        state.setAttempts(0);
        state.setLockoutUntil(null);
        state.setElevatedToken(elevatedToken);
        state.setElevatedExpiry(now.plusMinutes(30));

        auditLogService.log("PayrollSecurity", userId, AuditAction.UPDATE,
                "Payroll access OTP verified successfully. Elevated session granted for 30 minutes.", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Payroll access verified successfully.");
        response.put("elevatedToken", elevatedToken);
        response.put("expiresInMinutes", 30);
        return response;
    }

    public boolean isElevatedAccessValid(Long userId, String token) {
        if (userId == null || token == null || token.trim().isEmpty()) return false;
        OtpState state = userOtpMap.get(userId);
        if (state == null || state.getElevatedToken() == null || state.getElevatedExpiry() == null) {
            return false;
        }
        if (!state.getElevatedToken().equals(token.trim())) {
            return false;
        }
        return LocalDateTime.now().isBefore(state.getElevatedExpiry());
    }
}
