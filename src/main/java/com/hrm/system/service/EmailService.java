package com.hrm.system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;


    public void sendOtp(String toEmail, String subject, int otp){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText("Your verification code is: " + otp + "\n\nThis code expires in 10 minutes.");
        mailSender.send(message);
    }

    public void sendPayrollNotification(String toEmail, String month, int year) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Payroll Generated - " + month + " " + year);

            // HTML email template
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

            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("✓ Email sent successfully to: " + toEmail);

        } catch (Exception e) {
            System.err.println("✗ Failed to send email to: " + toEmail);
            e.printStackTrace();
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }
}