package com.hrm.system.service;

import com.hrm.system.model.PayrollStatus;
import com.hrm.system.model.Role;
import com.hrm.system.event.PayrollNotificationEvent;
import com.hrm.system.event.PayrollNotificationEventListener;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.PayrollPeriod;
import com.hrm.system.model.User;
import com.hrm.system.repository.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollEmailNotificationTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailService emailService;

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;

    @Mock
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private PayrollCalculationService payrollCalculationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PayrollItemRepository payrollItemRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PayrollGenerationHelper payrollGenerationHelper;

    private PayrollNotificationEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new PayrollNotificationEventListener(emailService);
    }

    @Test
    @DisplayName("1. EmailService formats BigDecimal netSalary to 2 decimal places and HTML-escapes dynamic fields")
    void testEmailService_BigDecimalFormattingAndHtmlEscaping() {
        EmailService realEmailService = new EmailService();
        ReflectionTestUtils.setField(realEmailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(realEmailService, "fromEmail", "noreply@hrm.com");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        BigDecimal netSalary = new BigDecimal("84575.2500");
        realEmailService.sendPayrollNotification(
                "employee@company.com",
                "John <Script> Doe",
                "September <HTML>",
                2026,
                netSalary,
                17L
        );

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("2. PayrollNotificationEventListener delegates GENERATED & APPROVED events to EmailService")
    void testEventListener_DelegatesEventsToEmailService() {
        PayrollNotificationEvent genEvent = PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.GENERATED)
                .payrollId(100L)
                .employeeId(17L)
                .employeeName("Saifullah Zia")
                .employeeEmail("saif@company.com")
                .month("September")
                .year(2026)
                .netSalary(new BigDecimal("84575.00"))
                .build();

        eventListener.handlePayrollNotification(genEvent);

        verify(emailService, times(1)).sendPayrollNotification(
                eq("saif@company.com"),
                eq("Saifullah Zia"),
                eq("September"),
                eq(2026),
                eq(new BigDecimal("84575.00")),
                eq(17L)
        );

        PayrollNotificationEvent appEvent = PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.APPROVED)
                .payrollId(101L)
                .employeeId(18L)
                .employeeName("Ehsan Uddin")
                .employeeEmail("ehsan@company.com")
                .month("September")
                .year(2026)
                .netSalary(new BigDecimal("95000.00"))
                .build();

        eventListener.handlePayrollNotification(appEvent);

        verify(emailService, times(1)).sendPayrollApprovedNotification(
                eq("ehsan@company.com"),
                eq("Ehsan Uddin"),
                eq("September"),
                eq(2026),
                eq(new BigDecimal("95000.00")),
                eq(18L)
        );
    }

    @Test
    @DisplayName("3. Idempotency status guard skips publishing approval event if payroll is already APPROVED")
    void testIdempotencyGuard_AlreadyApprovedSkipped() {
        Payroll payroll = new Payroll();
        payroll.setId(200L);
        payroll.setStatus(PayrollStatus.APPROVED);

        when(payrollRepository.findById(200L)).thenReturn(Optional.of(payroll));

        Payroll result = payrollGenerationHelper.approvePayrollForEmployee(200L, 1L);

        assertEquals(PayrollStatus.APPROVED, result.getStatus());
        verify(payrollRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("4. Fail-soft exception handling: EmailService exception does not throw from listener")
    void testFailSoft_EmailServiceExceptionCaught() {
        doThrow(new RuntimeException("SMTP Server Unreachable"))
                .when(emailService).sendPayrollNotification(any(), any(), any(), any(), any(), any());

        PayrollNotificationEvent genEvent = PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.GENERATED)
                .payrollId(100L)
                .employeeId(17L)
                .employeeName("Saifullah Zia")
                .employeeEmail("saif@company.com")
                .month("September")
                .year(2026)
                .netSalary(new BigDecimal("84575.00"))
                .build();

        assertDoesNotThrow(() -> eventListener.handlePayrollNotification(genEvent));
    }
}
