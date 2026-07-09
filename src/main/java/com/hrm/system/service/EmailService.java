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

    // Send Leave Request Notification to Admins
    public void sendLeaveRequestNotification(
            String toEmail,
            String employeeName,
            String leaveType,
            String startDate,
            String endDate,
            int duration,
            String reason
    ) {
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><style>
                body { font-family: Arial, sans-serif; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #4F46E5; color: white; padding: 20px; text-align: center; }
                .content { padding: 20px; background-color: #f9fafb; }
                .footer { text-align: center; padding: 20px; font-size: 12px; color: #6b7280; }
                .reason-box { background-color: #f3f4f6; padding: 15px; border-radius: 8px; margin: 10px 0; }
            </style></head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>📋 New Leave Request</h2>
                    </div>
                    <div class="content">
                        <p>A new leave request has been submitted:</p>
                        <p><strong>Employee:</strong> %s</p>
                        <p><strong>Leave Type:</strong> %s</p>
                        <p><strong>Duration:</strong> %s to %s (%d day(s))</p>
                        <div class="reason-box">
                            <p><strong>Reason:</strong></p>
                            <p>%s</p>
                        </div>
                        <p>Please login to the HRM system to review and approve/reject this request.</p>
                        <p>Regards,<br>HRM System</p>
                    </div>
                    <div class="footer">
                        <p>© 2026 JCAT Solutions HRM. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(employeeName, leaveType, startDate, endDate, duration, reason);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "New Leave Request - " + employeeName, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("New Leave Request - " + employeeName);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Leave request email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send leave request email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send Announcement Notification to Employees
    public void sendAnnouncementNotification(
            String toEmail,
            String employeeName,
            String announcementTitle,
            String announcementContent
    ) {
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><style>
                body { font-family: Arial, sans-serif; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #F59E0B; color: white; padding: 20px; text-align: center; }
                .content { padding: 20px; background-color: #f9fafb; }
                .footer { text-align: center; padding: 20px; font-size: 12px; color: #6b7280; }
                .announcement-box { background-color: #fffbeb; padding: 20px; border-radius: 8px; margin: 15px 0; border-left: 4px solid #F59E0B; }
            </style></head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>📢 New Announcement</h2>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>A new announcement has been published:</p>
                        <div class="announcement-box">
                            <h3>%s</h3>
                            <p>%s</p>
                        </div>
                        <p>Please login to the HRM system to view all announcements.</p>
                        <p>Regards,<br>HR Department</p>
                    </div>
                    <div class="footer">
                        <p>© 2026 JCAT Solutions HRM. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(employeeName, announcementTitle, announcementContent);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "New Announcement: " + announcementTitle, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("New Announcement: " + announcementTitle);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Announcement email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send announcement email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send Leave Approved Notification to Employee
    public void sendLeaveApprovedNotification(
            String toEmail,
            String employeeName,
            String leaveType,
            String startDate,
            String endDate,
            int duration
    ) {
        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head><style>
            body { font-family: Arial, sans-serif; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background-color: #16A34A; color: white; padding: 20px; text-align: center; }
            .content { padding: 20px; background-color: #f9fafb; }
            .footer { text-align: center; padding: 20px; font-size: 12px; color: #6b7280; }
            .details-box { background-color: #f0fdf4; padding: 15px; border-radius: 8px; margin: 10px 0; border-left: 4px solid #16A34A; }
        </style></head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>✅ Leave Approved</h2>
                </div>
                <div class="content">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your leave request has been <strong>approved</strong>.</p>
                    <div class="details-box">
                        <p><strong>Leave Type:</strong> %s</p>
                        <p><strong>Duration:</strong> %s to %s (%d day(s))</p>
                    </div>
                    <p>Please login to the HRM system for more details.</p>
                    <p>Regards,<br>HR Department</p>
                </div>
                <div class="footer">
                    <p>© 2026 JCAT Solutions HRM. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
    """.formatted(employeeName, leaveType, startDate, endDate, duration);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "Leave Request Approved - " + leaveType, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Leave Request Approved - " + leaveType);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Leave approved email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send leave approved email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send Leave Rejected Notification to Employee
    public void sendLeaveRejectedNotification(
            String toEmail,
            String employeeName,
            String leaveType,
            String startDate,
            String endDate,
            int duration
    ) {
        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head><style>
            body { font-family: Arial, sans-serif; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background-color: #DC2626; color: white; padding: 20px; text-align: center; }
            .content { padding: 20px; background-color: #f9fafb; }
            .footer { text-align: center; padding: 20px; font-size: 12px; color: #6b7280; }
            .details-box { background-color: #fef2f2; padding: 15px; border-radius: 8px; margin: 10px 0; border-left: 4px solid #DC2626; }
        </style></head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>❌ Leave Rejected</h2>
                </div>
                <div class="content">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your leave request has been <strong>rejected</strong>.</p>
                    <div class="details-box">
                        <p><strong>Leave Type:</strong> %s</p>
                        <p><strong>Duration:</strong> %s to %s (%d day(s))</p>
                    </div>
                    <p>Please login to the HRM system for more details or contact HR if you have questions.</p>
                    <p>Regards,<br>HR Department</p>
                </div>
                <div class="footer">
                    <p>© 2026 JCAT Solutions HRM. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
    """.formatted(employeeName, leaveType, startDate, endDate, duration);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "Leave Request Rejected - " + leaveType, htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Leave Request Rejected - " + leaveType);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Leave rejected email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send leave rejected email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // Send Salary Reveal OTP to Admin
    public void sendSalaryOtpEmail(String toEmail, String adminName, int code) {
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><style>
                body { font-family: Arial, sans-serif; margin: 0; padding: 0; background: #f3f4f6; }
                .container { max-width: 560px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 24px rgba(0,0,0,0.08); }
                .header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); color: white; padding: 32px 24px; text-align: center; }
                .header h2 { margin: 0; font-size: 22px; font-weight: 700; letter-spacing: 0.5px; }
                .header p { margin: 6px 0 0; font-size: 13px; color: rgba(255,255,255,0.7); }
                .content { padding: 32px 24px; }
                .otp-box { background: linear-gradient(135deg, #FC0175 0%, #a8005e 100%); border-radius: 12px; padding: 24px; text-align: center; margin: 20px 0; }
                .otp-code { font-size: 42px; font-weight: 900; color: #ffffff; letter-spacing: 10px; font-family: 'Courier New', monospace; }
                .otp-label { font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 6px; text-transform: uppercase; letter-spacing: 1.5px; }
                .info { background: #fff8f0; border: 1px solid #fde68a; border-radius: 8px; padding: 14px 16px; font-size: 13px; color: #92400e; margin-top: 18px; }
                .footer { text-align: center; padding: 20px; font-size: 11px; color: #9ca3af; background: #f9fafb; border-top: 1px solid #f3f4f6; }
            </style></head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>🔐 Salary Reveal Verification</h2>
                        <p>JCAT Solutions HRM System</p>
                    </div>
                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        <p>You requested to reveal an employee's salary. Use the verification code below to confirm your identity:</p>
                        <div class="otp-box">
                            <div class="otp-code">%06d</div>
                            <div class="otp-label">Verification Code</div>
                        </div>
                        <div class="info">
                            ⏱️ This code expires in <strong>5 minutes</strong>. Do not share this code with anyone.
                        </div>
                        <p style="margin-top:20px; color: #6b7280; font-size: 13px;">If you did not request this, please ignore this email. No salary data has been revealed.</p>
                        <p>Regards,<br><strong>JCAT Solutions HRM</strong></p>
                    </div>
                    <div class="footer">© 2026 JCAT Solutions HRM. All rights reserved.</div>
                </div>
            </body>
            </html>
        """.formatted(adminName, code);

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            sendViaResend(toEmail, "🔐 Salary Reveal Code - " + String.format("%06d", code), htmlContent, true);
            return;
        }

        // Fallback to SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🔐 Salary Reveal Code - " + String.format("%06d", code));
            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Salary OTP email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("✗ Failed to send salary OTP email to: " + toEmail);
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
