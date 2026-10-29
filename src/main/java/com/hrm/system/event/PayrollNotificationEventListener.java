package com.hrm.system.event;

import com.hrm.system.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollNotificationEventListener {

    private final EmailService emailService;

    @Async("payrollEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePayrollNotification(PayrollNotificationEvent event) {
        try {
            if (event == null) return;
            log.info("Processing payroll notification event: type={} payrollId={} employeeId={}",
                    event.getType(), event.getPayrollId(), event.getEmployeeId());

            if (event.getEmployeeEmail() == null || event.getEmployeeEmail().trim().isEmpty()) {
                log.warn("⚠️ Cannot send payroll notification: Employee ID {} ({}) has no email address configured.",
                        event.getEmployeeId(), event.getEmployeeName());
                return;
            }

            // Employees are emailed ONLY when the payroll is marked PAID.
            // GENERATED (draft) and APPROVED events are intentionally ignored.
            if (event.getType() != PayrollNotificationEvent.NotificationType.PAID) {
                log.debug("Skipping payroll email for event type {} (emails are sent only on PAID)", event.getType());
                return;
            }

            emailService.sendPayrollPaidNotification(
                    event.getEmployeeEmail(),
                    event.getEmployeeName(),
                    event.getMonth(),
                    event.getYear(),
                    event.getNetSalary(),
                    event.getEmployeeId()
            );
        } catch (Exception ex) {
            log.error("✗ Failed to send payroll notification email to {} (Employee ID {}): {}",
                    event != null ? event.getEmployeeEmail() : "unknown",
                    event != null ? event.getEmployeeId() : "unknown",
                    ex.getMessage(), ex);
        }
    }
}
