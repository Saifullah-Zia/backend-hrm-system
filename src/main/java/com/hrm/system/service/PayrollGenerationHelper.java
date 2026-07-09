package com.hrm.system.service;

import com.hrm.system.model.*;
import com.hrm.system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

        // Skip if payroll already exists for this employee + period
        Optional<Payroll> existing = payrollRepository.findByUserAndPayrollPeriod(employee, payrollPeriod);
        if (existing.isPresent()) {
            System.out.println("ℹ Payroll already exists for employee " + employeeId + " — skipping.");
            return false;
        }

        // Look up or generate attendance summary
        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .orElseGet(() -> {
                    attendanceService.generateAttendanceSummary(employeeId, payrollPeriodId);
                    return attendanceSummaryRepository
                            .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                            .orElseThrow(() -> new RuntimeException(
                                    "Failed to generate attendance summary for employee: " + employeeId));
                });

        double basicSalary   = employee.getBasicSalary() != null ? employee.getBasicSalary() : 0.0;
        int    workingDays   = attendanceSummary.getWorkingDays()    != null ? attendanceSummary.getWorkingDays()    : 26;
        double dailySalary   = payrollCalculationService.calculateDailySalary(basicSalary, workingDays);
        int    presentDays   = attendanceSummary.getPresentDays()    != null ? attendanceSummary.getPresentDays()    : 0;
        int    paidLeaveDays = attendanceSummary.getPaidLeaveDays()  != null ? attendanceSummary.getPaidLeaveDays()  : 0;
        int    unpaidLeave   = attendanceSummary.getUnpaidLeaveDays()!= null ? attendanceSummary.getUnpaidLeaveDays(): 0;
        int    absentDays    = attendanceSummary.getAbsentDays()     != null ? attendanceSummary.getAbsentDays()     : 0;
        int    lateDays      = attendanceSummary.getLateDays()       != null ? attendanceSummary.getLateDays()       : 0;

        double grossSalary = payrollCalculationService.calculateGrossSalary(basicSalary, presentDays, paidLeaveDays, 0.0, 0.0);
        double deductions  = payrollCalculationService.calculateDeductions(unpaidLeave, absentDays, lateDays, dailySalary, 0.0);
        double netSalary   = payrollCalculationService.calculateNetSalary(grossSalary, deductions);

        Payroll payroll = new Payroll();
        payroll.setPayrollPeriod(payrollPeriod);
        payroll.setUser(employee);
        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalary);
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(presentDays);
        payroll.setLateDays(lateDays);
        payroll.setPaidLeaveDays(paidLeaveDays);
        payroll.setUnpaidLeaveDays(unpaidLeave);
        payroll.setAbsentDays(absentDays);
        payroll.setTotalAllowances(0.0);
        payroll.setTotalBonuses(0.0);
        payroll.setTotalDeductions(deductions);
        payroll.setGrossSalary(grossSalary);
        payroll.setNetSalary(netSalary);
        payroll.setStatus(PayrollStatus.DRAFT);
        payroll.setGeneratedBy(generatedBy);
        payroll.setGeneratedAt(LocalDateTime.now());

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                employee.getId(),
                String.format("💰 Your payroll for %s %s has been generated. Net salary: %.2f",
                        payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalary),
                "PAYROLL",
                employee.getId(),
                saved.getId()
        );

        System.out.println("✓ Payroll generated for employee " + employeeId + " — net: " + netSalary);
        return true;
    }
}
