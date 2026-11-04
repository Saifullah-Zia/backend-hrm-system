package com.hrm.system.service;

import com.hrm.system.dto.auth.RegisterRequest;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int CODE_VALID_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVALID_LOGIN = "Invalid email or password.";
    private static final String INVALID_CODE = "Invalid or expired code. Please request a new one.";
    private static final String FORGOT_PASSWORD_RESPONSE =
            "If an account exists for that email, a password reset code has been sent.";
    private static final String RESEND_RESPONSE =
            "If that account still needs verification, a new code has been sent.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final LeaveBalanceService leaveBalanceService;
    private final String dummyPasswordHash;

    @Value("${office.allowed.ips:58.65.129.12}")
    private String allowedOfficeIps;

    @Value("${office.allow.localhost:false}")
    private boolean allowLocalhost;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       LeaveBalanceService leaveBalanceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.leaveBalanceService = leaveBalanceService;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-password");
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────
    public User authenticate(String email, String password, String clientIp) {
        String cleanEmail = clean(email);
        String submitted = password != null ? password : "";
        Optional<User> found = userRepository.findByEmailIgnoreCase(cleanEmail);

        if (found.isEmpty()) {
            // Spend the same time as a real check, so response time does not reveal which emails exist.
            passwordEncoder.matches(submitted, dummyPasswordHash);
            throw new BadCredentialsException(INVALID_LOGIN);
        }

        User user = found.get();
        if (!passwordEncoder.matches(submitted, user.getPassword())) {
            throw new BadCredentialsException(INVALID_LOGIN);
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Email not verified. Please check your inbox for the OTP.");
        }

        // Office Wi-Fi access restriction check for employees during login
        boolean isPrivileged = user.getRole() == Role.ADMIN || user.getRole() == Role.SUPERADMIN;
        if (!isPrivileged && !user.isOutsideAccessAllowed()
                && !com.hrm.system.util.IpUtil.isAllowedIp(clientIp, allowedOfficeIps, allowLocalhost)) {
            log.warn("Blocked login for employee {} from IP {} (outside Office Wi-Fi)", cleanEmail, clientIp);
            throw new RuntimeException("HRM login is restricted to Office Wi-Fi. Please connect to Office Wi-Fi or request remote access from HR.");
        }

        return user;
    }

    // ── REGISTER ───────────────────────────────────────────────────────────
    /** Public sign-up always creates a plain EMPLOYEE. Only a superadmin can grant other roles. */
    public String register(RegisterRequest request) {
        String email = clean(request.getEmail()).toLowerCase();
        String name = request.getName() != null ? request.getName().trim() : "";
        if (email.isEmpty() || !email.contains("@") || name.isEmpty()) {
            throw new RuntimeException("Name and a valid email are required.");
        }
        requireStrongPassword(request.getPassword());

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (existing.get().isEnabled()) {
                throw new RuntimeException("Email already registered.");
            }
            // Registered but never verified: send a new code instead of rejecting.
            sendVerificationCode(existing.get());
            return "Account already registered but not verified. A new verification code has been sent to your email.";
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.EMPLOYEE);
        user.setEnabled(false);
        sendVerificationCode(user);
        return "Registration successful. Please check your email for the verification code.";
    }

    // ── VERIFY EMAIL ───────────────────────────────────────────────────────
    public String verifyEmail(String email, String otp) {
        User user = userRepository.findByEmailIgnoreCase(clean(email))
                .orElseThrow(() -> new RuntimeException(INVALID_CODE));

        if (user.isEnabled()) {
            return "Account already verified.";
        }

        if (!isCodeUsable(user.getVerificationCode(), user.getVerificationExpiry())) {
            throw new RuntimeException(INVALID_CODE);
        }

        if (!codesMatch(user.getVerificationCode(), otp)) {
            int attempts = nextAttempt(user.getVerificationAttempts());
            user.setVerificationAttempts(attempts);
            if (attempts >= MAX_CODE_ATTEMPTS) {
                user.setVerificationCode(null);
                user.setVerificationExpiry(null);
            }
            userRepository.save(user);
            throw new RuntimeException(INVALID_CODE);
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        user.setVerificationAttempts(0);
        userRepository.save(user);

        // Initialize leave balances now that account is verified.
        // Probation starts when HR saves the employee profile with a joining date.
        leaveBalanceService.initializeBalancesForUser(user, LocalDate.now().getYear());
        return "Email verified successfully. You can now log in.";
    }

    // ── RESEND OTP ─────────────────────────────────────────────────────────
    public String resendVerificationOtp(String email) {
        userRepository.findByEmailIgnoreCase(clean(email))
                .filter(user -> !user.isEnabled())
                .ifPresent(this::sendVerificationCode);
        return RESEND_RESPONSE;
    }

    // ── FORGOT PASSWORD ────────────────────────────────────────────────────
    public String forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(clean(email)).ifPresent(user -> {
            String code = generateOtp();
            user.setResetPasswordCode(code);
            user.setResetPasswordExpiry(LocalDateTime.now().plusMinutes(CODE_VALID_MINUTES));
            user.setResetPasswordAttempts(0);
            userRepository.save(user);
            sendCode(user.getEmail(), "Reset your password", code);
        });
        return FORGOT_PASSWORD_RESPONSE;
    }

    // ── RESET PASSWORD ─────────────────────────────────────────────────────
    public String resetPassword(String email, String otp, String newPassword) {
        requireStrongPassword(newPassword);

        User user = userRepository.findByEmailIgnoreCase(clean(email))
                .orElseThrow(() -> new RuntimeException(INVALID_CODE));

        if (!isCodeUsable(user.getResetPasswordCode(), user.getResetPasswordExpiry())) {
            throw new RuntimeException(INVALID_CODE);
        }

        if (!codesMatch(user.getResetPasswordCode(), otp)) {
            int attempts = nextAttempt(user.getResetPasswordAttempts());
            user.setResetPasswordAttempts(attempts);
            if (attempts >= MAX_CODE_ATTEMPTS) {
                user.setResetPasswordCode(null);
                user.setResetPasswordExpiry(null);
            }
            userRepository.save(user);
            throw new RuntimeException(INVALID_CODE);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setResetPasswordCode(null);
        user.setResetPasswordExpiry(null);
        user.setResetPasswordAttempts(0);
        userRepository.save(user);

        return "Password reset successful. You can now log in.";
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void sendVerificationCode(User user) {
        String code = generateOtp();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(CODE_VALID_MINUTES));
        user.setVerificationAttempts(0);
        userRepository.save(user);
        sendCode(user.getEmail(), "Verify your account", code);
    }

    /** A mail failure is logged rather than returned, so the response never reveals whether an account exists. */
    private void sendCode(String email, String subject, String code) {
        try {
            emailService.sendOtp(email, subject, Integer.parseInt(code));
        } catch (RuntimeException e) {
            log.error("Failed to send \"{}\" email: {}", subject, e.getMessage());
        }
    }

    private static boolean isCodeUsable(String code, LocalDateTime expiry) {
        return code != null && expiry != null && LocalDateTime.now().isBefore(expiry);
    }

    private static boolean codesMatch(String expected, String submitted) {
        if (submitted == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static int nextAttempt(Integer attempts) {
        return (attempts == null ? 0 : attempts) + 1;
    }

    private static void requireStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static String clean(String email) {
        return email != null ? email.trim() : "";
    }

    private static String generateOtp() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}
