package com.hrm.system.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    // Send simple text emails
    public void sendSimpleMessage(String toEmail, String subject, String text) {
        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, subject, text, false);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
            System.out.println("✓ Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send OTP
    public void sendOtp(String toEmail, String subject, int otp) {
        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String htmlContent = "Your verification code is: <b>" + otp + "</b><br><br>This code expires in 10 minutes.";
            sendViaResend(toEmail, subject, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(
                    "Your verification code is: <b>" + otp + "</b><br><br>This code expires in 10 minutes.",
                    true
            );
            mailSender.send(message);
            System.out.println("✓ OTP email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send OTP to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send Payroll Notification
    public void sendPayrollNotification(String toEmail, String month, int year) {
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><style>
                body { font-family: Arial, sans-serif; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #4F46E5; color: white; padding: 20px; text-align: center; }
                .content { padding: 20px; background-color: #f9fafb; }
                .footer { text-align: center; padding: 20px; font-size: 12px; color: #6b7280; }
            </style></head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>Payroll Generated</h2>
                    </div>
                    <div class="content">
                        <p>Dear Employee,</p>
                        <p>Your payroll for <strong>%s %d</strong> has been generated.</p>
                        <p>Please login to the HRM system to view your payslip.</p>
                        <p>Regards,<br>HR Department</p>
                    </div>
                    <div class="footer">
                        <p>© 2026 HRM System. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(month, year);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "Payroll Generated - " + month + " " + year, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Payroll Generated - " + month + " " + year);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Payroll email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send payroll email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Helper method to send email via Resend API
    private void sendViaResend(String toEmail, String subject, String content, boolean isHtml) {
        try {
            String apiKey = System.getenv("RESEND_API_KEY");
            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from", "JCAT Solutions HRM <" + fromEmail + ">");
            body.put("to", new String[]{toEmail});
            body.put("subject", subject);
            if (isHtml) {
                body.put("html", content);
            } else {
                body.put("text", content);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✓ Email sent via Resend API to: " + toEmail);
            } else {
                throw new RuntimeException("Resend API returned status code: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to send email via Resend API to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Resend Email sending failed: " + e.getMessage(), e);
        }
    }
}
