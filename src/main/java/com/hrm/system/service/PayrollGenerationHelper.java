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
     * @return true if a new payroll record was created, false if it was skipped
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean generatePayrollForEmployee(Long payrollPeriodId, Long employeeId, Long generatedBy) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found: " + payrollPeriodId));

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        // Skip non-EMPLOYEE users (ADMIN and SUPERADMIN should not get payroll generated)
        if (employee.getRole() != Role.EMPLOYEE) {
            System.out.println("ℹ User " + employeeId + " (" + employee.getRole() + ") is not Role.EMPLOYEE — skipping.");
            return false;
        }

        // Skip if payroll already exists for this employee + period
        Optional<Payroll> existing = payrollRepository.findByUserAndPayrollPeriod(employee, payrollPeriod);
        if (existing.isPresent()) {
            System.out.println("ℹ Payroll already exists for employee " + employeeId + " — skipping.");
            return false;
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
        
        int daysInMonth = 30;
        try {
            String mStr = payrollPeriod.getMonth();
            if (mStr != null && mStr.contains(" ")) mStr = mStr.split(" ")[0];
            if (mStr != null && payrollPeriod.getYear() != null) {
                String cleanMonth = mStr.trim().toUpperCase();
                if (cleanMonth.length() > 3) {
                    java.time.YearMonth ym = java.time.YearMonth.of(payrollPeriod.getYear(), java.time.Month.valueOf(cleanMonth));
                    daysInMonth = ym.lengthOfMonth();
                }
            }
        } catch (Exception e) {
            daysInMonth = workingDays > 0 ? workingDays : 30;
        }

        BigDecimal dailySalaryBd   = payrollCalculationService.calculateDailySalary(basicSalaryBd, daysInMonth);
        int        presentDays     = attendanceSummary.getPresentDays()    != null ? attendanceSummary.getPresentDays()    : 0;
        int        paidLeaveDays   = attendanceSummary.getPaidLeaveDays()  != null ? attendanceSummary.getPaidLeaveDays()  : 0;
        int        unpaidLeave     = attendanceSummary.getUnpaidLeaveDays()!= null ? attendanceSummary.getUnpaidLeaveDays(): 0;
        int        absentDays      = attendanceSummary.getAbsentDays()     != null ? attendanceSummary.getAbsentDays()     : 0;
        int        lateDays        = attendanceSummary.getLateDays()       != null ? attendanceSummary.getLateDays()       : 0;

        BigDecimal grossSalaryBd   = payrollCalculationService.calculateGrossSalary(basicSalaryBd, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal incomeTaxBd     = payrollCalculationService.calculateIncomeTax(grossSalaryBd);
        BigDecimal deductionsBd    = payrollCalculationService.calculateDeductions(unpaidLeave, absentDays, lateDays, dailySalaryBd, BigDecimal.ZERO, incomeTaxBd);
        BigDecimal netSalaryBd     = payrollCalculationService.calculateNetSalary(grossSalaryBd, deductionsBd);

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
        payroll.setTotalAllowances(0.0);
        payroll.setTotalBonuses(0.0);
        payroll.setTotalDeductions(deductionsBd.doubleValue());
        payroll.setGrossSalary(grossSalaryBd.doubleValue());
        payroll.setNetSalary(netSalaryBd.doubleValue());
        payroll.setStatus(PayrollStatus.DRAFT);
        payroll.setGeneratedBy(generatedBy);
        payroll.setGeneratedAt(LocalDateTime.now());

        Payroll saved = payrollRepository.save(payroll);

        if (incomeTaxBd.compareTo(BigDecimal.ZERO) > 0) {
            PayrollItem taxItem = new PayrollItem();
            taxItem.setPayroll(saved);
            taxItem.setType(PayrollItemType.DEDUCTION);
            taxItem.setName("Income Tax");
            taxItem.setAmount(incomeTaxBd.doubleValue());
            taxItem.setDescription(payrollCalculationService.getAppliedTaxDescription(grossSalaryBd));
            payrollItemRepository.save(taxItem);
        }

        notificationService.createNotification(
                employee.getId(),
                String.format("💰 Your payroll for %s %s has been generated. Net salary: %.2f",
                        payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalaryBd.doubleValue()),
                "PAYROLL",
                employee.getId(),
                saved.getId()
        );

        System.out.println("✓ Payroll generated for employee " + employeeId + " — net: " + netSalaryBd.doubleValue());

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.GENERATE,
                String.format("Bulk payroll generated for %s — period: %s %s, net: %.2f",
                        employee.getName(), payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalaryBd.doubleValue()),
                generatedBy);

        // Publish event for AFTER_COMMIT async email notification
        eventPublisher.publishEvent(PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.GENERATED)
                .payrollId(saved.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeeEmail(employee.getEmail())
                .month(payrollPeriod.getMonth())
                .year(payrollPeriod.getYear())
                .netSalary(netSalaryBd)
                .build());

        return true;
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

        BigDecimal netSalaryBd = saved.getNetSalary() != null ? BigDecimal.valueOf(saved.getNetSalary()) : BigDecimal.ZERO;

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll for %s %s has been approved. Net salary: %.2f",
                        payroll.getPayrollPeriod().getMonth(), payroll.getPayrollPeriod().getYear(),
                        saved.getNetSalary()),
                "PAYROLL", payroll.getUser().getId(), saved.getId()
        );

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.APPROVE,
                String.format("Payroll approved for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                approvedBy);

        // Publish event for AFTER_COMMIT async email notification
        eventPublisher.publishEvent(PayrollNotificationEvent.builder()
                .type(PayrollNotificationEvent.NotificationType.APPROVED)
                .payrollId(saved.getId())
                .employeeId(payroll.getUser().getId())
                .employeeName(payroll.getUser().getName())
                .employeeEmail(payroll.getUser().getEmail())
                .month(payroll.getPayrollPeriod().getMonth())
                .year(payroll.getPayrollPeriod().getYear())
                .netSalary(netSalaryBd)
                .build());

        return saved;
    }
}
