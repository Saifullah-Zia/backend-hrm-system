package com.hrm.system.controller;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.dto.auth.LoginRequest;
import com.hrm.system.dto.auth.RegisterResponse;
import com.hrm.system.model.User;
import com.hrm.system.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    // Verify email OTP
    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String email,
                                              @RequestParam String otp) {
        return ResponseEntity.ok(authService.verifyEmail(email, otp));
    }

    // Resend OTP
    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@RequestParam String email) {
        return ResponseEntity.ok(authService.resendVerificationOtp(email));
    }

    // Forgot password — sends OTP
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        return ResponseEntity.ok(authService.forgotPassword(email));
    }

    // Reset password with OTP
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String email,
                                                @RequestParam String otp,
                                                @RequestParam String newPassword) {
        return ResponseEntity.ok(authService.resetPassword(email, otp, newPassword));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getEmail(), request.getPassword());

        String accessToken  = jwtUtil.generateToken(
                user.getName(), user.getRole().name(), user.getId(), user.getEmail());

        String refreshToken = jwtUtil.generateRefreshToken(
                user.getName(), user.getRole().name(), user.getId(), user.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken",  accessToken);
        response.put("refreshToken", refreshToken);
        response.put("userId",       user.getId());
        response.put("role",         user.getRole().name());
        response.put("name",         user.getName());
        response.put("email",        user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        return authService.register(user);
    }
}