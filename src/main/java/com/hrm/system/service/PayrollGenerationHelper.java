package com.hrm.system.service;

import com.hrm.system.model.*;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.repository.*;
import com.hrm.system.event.PayrollNotificationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Helper service that runs each individual employee payroll generation in its
 * OWN independent database transaction (REQUIRES_NEW).
 *
 * This is necessary because Spring's default REQUIRED propagation shares one
 * transaction for the entire bulk loop. If a single employee's payroll throws
 * (e.g. duplicate key), the shared transaction is marked "rollback-only" and
 * ALL subsequent saves fail, even inside a try/catch.
 *
 * By using REQUIRES_NEW here, every employee gets a brand-new transaction that
 * is completely independent of the outer bulk transaction — if one fails, the
 * others are unaffected.
 */
@Service
public class PayrollGenerationHelper {

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    @Autowired
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PayrollCalculationService payrollCalculationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PayrollItemRepository payrollItemRepository;

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Generate payroll for a single employee inside a BRAND NEW transaction.
     * If this throws, only this employee's transaction is rolled back —
     * the outer bulk transaction is completely unaffected.
     *
     * @return null if a new payroll record was created, otherwise the reason it was skipped
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generatePayrollForEmployee(Long payrollPeriodId, Long employeeId, Long generatedBy) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found: " + payrollPeriodId));

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        // Skip non-EMPLOYEE users (ADMIN and SUPERADMIN should not get payroll generated)
        if (employee.getRole() != Role.EMPLOYEE) {
            System.out.println("ℹ User " + employeeId + " (" + employee.getRole() + ") is not Role.EMPLOYEE — skipping.");
            return "not an employee account";
        }

        // Skip if payroll already exists for this employee + period
        Optional<Payroll> existing = payrollRepository.findByUserAndPayrollPeriod(employee, payrollPeriod);
        if (existing.isPresent()) {
            System.out.println("ℹ Payroll already exists for employee " + employeeId + " — skipping.");
            return "already exists";
        }

        String ineligibleReason = payrollIneligibilityReason(employee);
        if (ineligibleReason != null) {
            System.out.println("ℹ Employee " + employeeId + " skipped — " + ineligibleReason);
            return ineligibleReason;
        }

        // Look up attendance summary or construct fallback
        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .orElseGet(() -> {
                    AttendanceSummary fallback = new AttendanceSummary();
                    fallback.setEmployee(employee);
                    fallback.setPayrollPeriod(payrollPeriod);
                    fallback.setPresentDays(0);
                    fallback.setLateDays(0);
                    fallback.setPaidLeaveDays(0);
                    fallback.setUnpaidLeaveDays(0);
                    fallback.setAbsentDays(0);
                    fallback.setWorkingDays(26);
                    return fallback;
                });

        BigDecimal basicSalaryBd = BigDecimal.ZERO;
        if (employee.getBasicSalary() != null && employee.getBasicSalary() > 0) {
            basicSalaryBd = BigDecimal.valueOf(employee.getBasicSalary());
        } else if (employeeProfileRepository != null) {
            Optional<EmployeeProfile> profileOpt = employeeProfileRepository.findByUserId(employeeId);
            if (profileOpt.isPresent() && profileOpt.get().getBasicSalary() != null && profileOpt.get().getBasicSalary() > 0) {
                basicSalaryBd = BigDecimal.valueOf(profileOpt.get().getBasicSalary());
            }
        }

        int workingDays = attendanceSummary.getWorkingDays() != null ? attendanceSummary.getWorkingDays() : 26;

        // Daily rate = monthly salary / calendar days of the month (company rule)
        BigDecimal dailySalaryBd   = payrollCalculationService.calculateDailySalaryForMonth(
                basicSalaryBd, payrollPeriod.getMonth(), payrollPeriod.getYear());
        int        presentDays     = attendanceSummary.getPresentDays()    != null ? attendanceSummary.getPresentDays()    : 0;
        int        paidLeaveDays   = attendanceSummary.getPaidLeaveDays()  != null ? attendanceSummary.getPaidLeaveDays()  : 0;
        int        unpaidLeave     = attendanceSummary.getUnpaidLeaveDays()!= null ? attendanceSummary.getUnpaidLeaveDays(): 0;
        int        absentDays      = attendanceSummary.getAbsentDays()     != null ? attendanceSummary.getAbsentDays()     : 0;
        int        lateDays        = attendanceSummary.getLateDays()       != null ? attendanceSummary.getLateDays()       : 0;

        Payroll payroll = new Payroll();
        payroll.setPayrollPeriod(payrollPeriod);
        payroll.setUser(employee);
        payroll.setBasicSalary(basicSalaryBd.doubleValue());
        payroll.setDailySalary(dailySalaryBd.doubleValue());
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(presentDays);
        payroll.setLateDays(lateDays);
        payroll.setPaidLeaveDays(paidLeaveDays);
        payroll.setUnpaidLeaveDays(unpaidLeave);
        payroll.setAbsentDays(absentDays);
        payroll.setHolidayDays(attendanceSummary.getHolidayDays() != null ? attendanceSummary.getHolidayDays() : 0);
        payroll.setTotalAllowances(0.0);
        payroll.setTotalBonuses(0.0);
        payroll.setManualDeduction(0.0);
        payroll.setStatus(PayrollStatus.DRAFT);
        payroll.setGeneratedBy(generatedBy);
        payroll.setGeneratedAt(LocalDateTime.now());

        BigDecimal incomeTaxBd = payrollCalculationService.recalculateTotals(payroll);
        BigDecimal netSalaryBd = BigDecimal.valueOf(payroll.getNetSalary());

        Payroll saved = payrollRepository.save(payroll);
        syncIncomeTaxItem(saved, incomeTaxBd);

        // No employee notification here — employees are only notified when payroll is PAID.

        System.out.println("✓ Payroll generated for employee " + employeeId + " — net: " + netSalaryBd.doubleValue());

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.GENERATE,
                String.format("Bulk payroll generated for %s — period: %s %s, net: %.2f",
                        employee.getName(), payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalaryBd.doubleValue()),
                generatedBy);

        return null;
    }

    /**
     * Why an employee should not get a payroll (unverified sign-up, not ACTIVE,
     * or no basic salary set), or null when they are eligible.
     */
    public String payrollIneligibilityReason(User employee) {
        if (!employee.isEnabled()) {
            return "email not verified";
        }
        Optional<EmployeeProfile> profile = employeeProfileRepository.findByUserId(employee.getId());
        EmploymentStatus status = profile.map(EmployeeProfile::getEmploymentStatus).orElse(null);
        if (status == EmploymentStatus.INACTIVE || status == EmploymentStatus.TERMINATED) {
            return "employment status is " + status;
        }
        boolean hasSalary = (employee.getBasicSalary() != null && employee.getBasicSalary() > 0)
                || profile.map(p -> p.getBasicSalary() != null && p.getBasicSalary() > 0).orElse(false);
        if (!hasSalary) {
            return "no basic salary set";
        }
        return null;
    }

    /**
     * Keep the single "Income Tax" deduction item in line with the tax actually
     * applied — created, updated or removed (when the tax is zero).
     */
    public void syncIncomeTaxItem(Payroll payroll, BigDecimal incomeTax) {
        PayrollItem taxItem = payrollItemRepository.findByPayrollId(payroll.getId()).stream()
                .filter(i -> i.getType() == PayrollItemType.DEDUCTION && "Income Tax".equals(i.getName()))
                .findFirst()
                .orElse(null);

        if (incomeTax == null || incomeTax.compareTo(BigDecimal.ZERO) <= 0) {
            if (taxItem != null) {
                if (payroll.getPayrollItems() != null) payroll.getPayrollItems().remove(taxItem);
                payrollItemRepository.delete(taxItem);
            }
            return;
        }

        if (taxItem == null) {
            taxItem = new PayrollItem();
            taxItem.setPayroll(payroll);
            taxItem.setType(PayrollItemType.DEDUCTION);
            taxItem.setName("Income Tax");
        }
        taxItem.setAmount(incomeTax.doubleValue());
        taxItem.setDescription(payroll.getFbrTaxOverride() != null
                ? "Income Tax (set by HR)"
                : payrollCalculationService.getAppliedTaxDescription(BigDecimal.valueOf(payroll.getBasicSalary() != null ? payroll.getBasicSalary() : 0.0)));
        payrollItemRepository.save(taxItem);
    }

    /**
     * Approve individual employee payroll inside a BRAND NEW transaction.
     * Each approval commits independently, ensuring partial-success resilience and
     * triggering AFTER_COMMIT email notifications immediately per employee.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payroll approvePayrollForEmployee(Long payrollId, Long approvedBy) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found: " + payrollId));

        // Idempotency status guard: if already APPROVED or PAID, skip status change & notification
        if (payroll.getStatus() == PayrollStatus.APPROVED || payroll.getStatus() == PayrollStatus.PAID) {
            return payroll;
        }

        payroll.setStatus(PayrollStatus.APPROVED);
        payroll.setApprovedBy(approvedBy);
        payroll.setApprovedAt(LocalDateTime.now());
        Payroll saved = payrollRepository.save(payroll);

        // No employee notification on approval — employees are only notified when payroll is PAID.

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.APPROVE,
                String.format("Payroll approved for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                approvedBy);

        return saved;
    }

    /**
     * Mark individual employee payroll as PAID inside a BRAND NEW transaction.
     * Each payment commits independently, ensuring partial-success resilience and
     * triggering AFTER_COMMIT notifications immediately per employee.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payroll payPayrollForEmployee(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found: " + payrollId));

        if (payroll.getStatus() == PayrollStatus.PAID) {
            return payroll;
        }

        payroll.setStatus(PayrollStatus.PAID);
        payroll.setPaidAt(LocalDateTime.now());
        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll for %s %s has been paid. Net salary: %.2f",
                        payroll.getPayrollPeriod().getMonth(), payroll.getPayrollPeriod().getYear(),
                        saved.getNetSalary()),
                "PAYROLL", payroll.getUser().getId(), saved.getId()
        );

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.UPDATE,
                String.format("Payroll marked as PAID for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                payroll.getApprovedBy() != null ? payroll.getApprovedBy() : payroll.getGeneratedBy());

        // Email the employee ONLY now that the payroll is PAID (sent AFTER_COMMIT, async).
        // The early-return above for already-PAID payrolls guarantees no duplicate email.
        BigDecimal paidNetSalaryBd = saved.getNetSalary() != null ? BigDecimal.valueOf(saved.getNetSalary()) : BigDecimal.ZERO;
        eventPublisher.publishEvent(PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.PAID)
                .payrollId(saved.getId())
                .employeeId(payroll.getUser().getId())
                .employeeName(payroll.getUser().getName())
                .employeeEmail(payroll.getUser().getEmail())
                .month(payroll.getPayrollPeriod().getMonth())
                .year(payroll.getPayrollPeriod().getYear())
                .netSalary(paidNetSalaryBd)
                .build());

        return saved;
    }
}
