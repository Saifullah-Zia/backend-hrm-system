package com.hrm.system.service;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.dto.auth.RegisterResponse;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final LeaveBalanceService leaveBalanceService;
    private final ProbationService probationService ;

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       LeaveBalanceService leaveBalanceService, ProbationService probationService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.leaveBalanceService = leaveBalanceService;
        this.probationService = probationService;
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────
    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Email not verified. Please check your inbox for the OTP.");
        }

        return user;
    }

    // ── REGISTER ───────────────────────────────────────────────────────────
    public String register(User user) {
        Optional<User> existing = userRepository.findByEmail(user.getEmail());

        if (existing.isPresent()) {
            if (!existing.get().isEnabled()) {
                // User registered but never verified — resend OTP instead of rejecting
                User unverified = existing.get();
                String otp = generateOtp();
                unverified.setVerificationCode(otp);
                unverified.setVerificationExpiry(LocalDateTime.now().plusMinutes(10));
                userRepository.save(unverified);

                try {
                    emailService.sendOtp(unverified.getEmail(), "Verify your account", Integer.parseInt(otp));
                } catch (Exception e) {
                    log.error("Failed to resend verification email: " + e.getMessage());
                }

                return "Account already registered but not verified. A new verification code has been sent to your email.";
            } else {
                throw new RuntimeException("Email already registered.");
            }
        }

        String otp = generateOtp();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(false);
        user.setVerificationCode(otp);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        userRepository.save(user);
        emailService.sendOtp(user.getEmail(), "Verify your account", Integer.parseInt(otp));
        return "Registration successful. Please check your email for the verification code.";

    }

    // ── VERIFY EMAIL ───────────────────────────────────────────────────────
    public String verifyEmail(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEnabled()) {
            return "Account already verified.";
        }

        if (!otp.equals(user.getVerificationCode())) {
            throw new RuntimeException("Invalid verification code.");
        }

        if (LocalDateTime.now().isAfter(user.getVerificationExpiry())) {
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        userRepository.save(user);

        // Initialize leave balances now that account is verified
        leaveBalanceService.initializeBalancesForUser(user, LocalDate.now().getYear());
        probationService.startProbation(user, null);
        return "Email verified successfully. You can now log in.";
    }

    // ── RESEND OTP ─────────────────────────────────────────────────────────
    public String resendVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEnabled()) {
            throw new RuntimeException("Account already verified.");
        }

        String otp = generateOtp();
        user.setVerificationCode(otp);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendOtp(email, "Verify your account", Integer.parseInt(otp));
        return "Verification code resent. Please check your email.";
    }

    // ── FORGOT PASSWORD ────────────────────────────────────────────────────
    public String forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with that email."));

        String otp = generateOtp();
        user.setResetPasswordCode(otp);
        user.setResetPasswordExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendOtp(email, "Reset your password", Integer.parseInt(otp));
        return "Password reset code sent to your email.";
    }

    // ── RESET PASSWORD ─────────────────────────────────────────────────────
    public String resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!otp.equals(user.getResetPasswordCode())) {
            throw new RuntimeException("Invalid reset code.");
        }

        if (LocalDateTime.now().isAfter(user.getResetPasswordExpiry())) {
            throw new RuntimeException("Reset code has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordCode(null);
        user.setResetPasswordExpiry(null);
        userRepository.save(user);

        return "Password reset successful. You can now log in.";
    }

    //helper
    private String generateOtp() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}