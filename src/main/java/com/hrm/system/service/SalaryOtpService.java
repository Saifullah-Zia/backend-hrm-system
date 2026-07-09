package com.hrm.system.service;

import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one-time passwords (OTPs) for revealing blurred employee salaries.
 * OTPs are stored in-memory, keyed by admin userId, and expire after 5 minutes.
 * No database changes are required.
 */
@Service
public class SalaryOtpService {

    private static final int OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final int OTP_LENGTH = 6;
    private static final int REVEAL_TOKEN_EXPIRY_SECONDS = 600; // 10 minutes

    /** In-memory store: adminUserId → OtpEntry */
    private final ConcurrentHashMap<Long, OtpEntry> otpStore = new ConcurrentHashMap<>();

    /** In-memory store: revealTokenValue → RevealTokenEntry */
    private final ConcurrentHashMap<String, RevealTokenEntry> revealTokenStore = new ConcurrentHashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    /**
     * Generates a new OTP, stores it, and emails it to the admin.
     * If a previous (unexpired) OTP exists for this admin, it is overwritten.
     *
     * @param adminUserId The ID of the logged-in admin requesting the reveal.
     * @throws RuntimeException if the admin is not found.
     */
    public void generateAndSend(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found: " + adminUserId));

        int code = generateCode();
        Instant expiry = Instant.now().plusSeconds(OTP_EXPIRY_SECONDS);
        otpStore.put(adminUserId, new OtpEntry(String.valueOf(code), expiry));

        emailService.sendSalaryOtpEmail(admin.getEmail(), admin.getName(), code);
        System.out.printf("✓ Salary OTP sent to admin %s (%s)%n", admin.getName(), admin.getEmail());
    }

    /**
     * Verifies the submitted OTP code for the given admin.
     * Clears the entry on success. Returns false if missing, expired, or wrong.
     *
     * @param adminUserId The ID of the admin submitting the code.
     * @param submittedCode The code typed by the admin.
     * @return true if the code is correct and not expired; false otherwise.
     */
    public boolean verify(Long adminUserId, String submittedCode) {
        OtpEntry entry = otpStore.get(adminUserId);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiry())) {
            otpStore.remove(adminUserId);
            return false;
        }
        boolean match = entry.code().equals(submittedCode.trim());
        if (match) {
            otpStore.remove(adminUserId); // one-time use
        }
        return match;
    }

    /**
     * Generates a secure random reveal token for the admin, valid for 10 minutes.
     */
    public String generateRevealToken(Long adminUserId) {
        String token = java.util.UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(REVEAL_TOKEN_EXPIRY_SECONDS);
        revealTokenStore.put(token, new RevealTokenEntry(adminUserId, expiry));
        return token;
    }

    /**
     * Validates whether the given reveal token is active, not expired, and belongs to the given admin.
     */
    public boolean isRevealTokenValid(String token, Long adminUserId) {
        if (token == null || token.trim().isEmpty() || adminUserId == null) return false;
        RevealTokenEntry entry = revealTokenStore.get(token.trim());
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiry())) {
            revealTokenStore.remove(token.trim());
            return false;
        }
        return entry.adminUserId().equals(adminUserId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int generateCode() {
        int min = (int) Math.pow(10, OTP_LENGTH - 1); // 100000
        int max = (int) Math.pow(10, OTP_LENGTH) - 1; // 999999
        return min + new Random().nextInt(max - min + 1);
    }

    /** Immutable value object to hold an OTP and its expiry timestamp. */
    private record OtpEntry(String code, Instant expiry) {}

    /** Immutable value object to hold a reveal token and its expiry. */
    private record RevealTokenEntry(Long adminUserId, Instant expiry) {}
}
