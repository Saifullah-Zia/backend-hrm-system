package com.hrm.system.controller;

import com.hrm.system.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class EmailTestController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send-payroll-email")
    public ResponseEntity<String> testPayrollEmail(
            @RequestParam String toEmail,
            @RequestParam String month,
            @RequestParam int year
    ) {
        try {
            emailService.sendPayrollNotification(toEmail, month, year);
            return ResponseEntity.ok("✅ Email sent successfully to " + toEmail);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Failed to send email: " + e.getMessage());
        }
    }
}