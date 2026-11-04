package com.hrm.system.controller;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.dto.auth.LoginRequest;
import com.hrm.system.dto.auth.RegisterRequest;
import com.hrm.system.model.User;
import com.hrm.system.security.RateLimiter;
import com.hrm.system.service.AuthService;
import com.hrm.system.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    // The whole office shares one public IP, so per-IP limits stay generous and per-email limits strict.
    private static final int LOGIN_FAILS_PER_EMAIL_AND_IP = 8;
    private static final int LOGIN_FAILS_PER_EMAIL = 30;
    private static final int LOGIN_FAILS_PER_IP = 100;
    private static final int EMAILS_PER_ADDRESS = 3;
    private static final int EMAILS_PER_IP = 30;
    private static final int CODE_CHECKS_PER_IP = 50;
    private static final int REGISTRATIONS_PER_IP = 30;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RateLimiter rateLimiter;

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String email,
                                              @RequestParam String otp,
                                              HttpServletRequest request) {
        rateLimiter.consume("code-check-ip:" + IpUtil.getClientIp(request), CODE_CHECKS_PER_IP, WINDOW);
        return ResponseEntity.ok(authService.verifyEmail(email, otp));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@RequestParam String email, HttpServletRequest request) {
        limitOutgoingEmail(email, request);
        return ResponseEntity.ok(authService.resendVerificationOtp(email));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email, HttpServletRequest request) {
        limitOutgoingEmail(email, request);
        return ResponseEntity.ok(authService.forgotPassword(email));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String email,
                                                @RequestParam String otp,
                                                @RequestParam String newPassword,
                                                HttpServletRequest request) {
        rateLimiter.consume("code-check-ip:" + IpUtil.getClientIp(request), CODE_CHECKS_PER_IP, WINDOW);
        return ResponseEntity.ok(authService.resetPassword(email, otp, newPassword));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        String email = normalize(request.getEmail());
        String emailKey = "login-email:" + email;
        String pairKey = "login-pair:" + email + "|" + clientIp;
        String ipKey = "login-ip:" + clientIp;

        rateLimiter.requireUnder(pairKey, LOGIN_FAILS_PER_EMAIL_AND_IP);
        rateLimiter.requireUnder(emailKey, LOGIN_FAILS_PER_EMAIL);
        rateLimiter.requireUnder(ipKey, LOGIN_FAILS_PER_IP);

        User user;
        try {
            user = authService.authenticate(request.getEmail(), request.getPassword(), clientIp);
        } catch (BadCredentialsException e) {
            rateLimiter.record(pairKey, WINDOW);
            rateLimiter.record(emailKey, WINDOW);
            rateLimiter.record(ipKey, WINDOW);
            throw e;
        }
        rateLimiter.clear(pairKey);
        rateLimiter.clear(emailKey);

        String accessToken = jwtUtil.generateToken(
                user.getEmail(), user.getRole().name(), user.getId(), user.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken",          accessToken);
        response.put("userId",               user.getId());
        response.put("role",                 user.getRole().name());
        response.put("name",                 user.getName());
        response.put("email",                user.getEmail());
        response.put("webCheckInAllowed",    user.isWebCheckInAllowed());
        response.put("outsideAccessAllowed", user.isOutsideAccessAllowed());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume("register-ip:" + IpUtil.getClientIp(httpRequest), REGISTRATIONS_PER_IP, Duration.ofHours(1));
        limitOutgoingEmail(request.getEmail(), httpRequest);
        return authService.register(request);
    }

    private void limitOutgoingEmail(String email, HttpServletRequest request) {
        rateLimiter.consume("email-ip:" + IpUtil.getClientIp(request), EMAILS_PER_IP, WINDOW);
        rateLimiter.consume("email-address:" + normalize(email), EMAILS_PER_ADDRESS, WINDOW);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
