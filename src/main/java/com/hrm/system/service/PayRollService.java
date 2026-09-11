package com.hrm.system.service;

import com.hrm.system.dto.AttendanceSummaryDto;
import com.hrm.system.dto.PayRollDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.*;
import com.hrm.system.repository.*;
import com.hrm.system.event.PayrollNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayRollService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    @Autowired
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Autowired
    private PayrollItemRepository payrollItemRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PayrollCalculationService payrollCalculationService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PayrollGenerationHelper payrollGenerationHelper;

    @Autowired
    private AuditLogService auditLogService;

    // ─── New payroll generation with attendance integration ───────────────────

    @Transactional
    public PayRollDto generatePayroll(Long payrollPeriodId, Long employeeId, Long generatedBy) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        if (!payrollPeriod.getLocked()) {
            throw new RuntimeException("Payroll period must be locked before generating payroll");
        }

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Optional<Payroll> existing = payrollRepository.findByUserAndPayrollPeriod(employee, payrollPeriod);
        if (existing.isPresent()) {
            throw new RuntimeException("Payroll already exists for this employee and period");
        }

        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .orElseGet(() -> {
                    attendanceService.generateAttendanceSummary(employeeId, payrollPeriodId);
                    return attendanceSummaryRepository.findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                            .orElseThrow(() -> new RuntimeException("Failed to generate attendance summary for employee: " + employeeId));
                });

        double basicSalary = employee.getBasicSalary() != null ? employee.getBasicSalary() : 0.0;
        int workingDays = attendanceSummary.getWorkingDays() != null ? attendanceSummary.getWorkingDays() : 26;

        // Calculate daily salary using calendar days of the payroll month
        int daysInMonth = 30;
        try {
            String mStr = payrollPeriod.getMonth();
            if (mStr != null && mStr.contains(" ")) mStr = mStr.split(" ")[0];
            if (mStr != null && payrollPeriod.getYear() != null) {
                java.time.YearMonth ym = java.time.YearMonth.of(payrollPeriod.getYear(), java.time.Month.valueOf(mStr.trim().toUpperCase()));
                daysInMonth = ym.lengthOfMonth();
            }
        } catch (Exception ex) {
            daysInMonth = workingDays > 0 ? workingDays : 30;
        }
        BigDecimal basicSalaryBd = BigDecimal.valueOf(basicSalary);
        BigDecimal dailySalaryBd = payrollCalculationService.calculateDailySalary(basicSalaryBd, daysInMonth);

        Payroll payroll = new Payroll();
        payroll.setPayrollPeriod(payrollPeriod);
        payroll.setUser(employee);
        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalaryBd.doubleValue());
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(attendanceSummary.getPresentDays());
        payroll.setLateDays(attendanceSummary.getLateDays());
        payroll.setPaidLeaveDays(attendanceSummary.getPaidLeaveDays());
        payroll.setUnpaidLeaveDays(attendanceSummary.getUnpaidLeaveDays());
        payroll.setAbsentDays(attendanceSummary.getAbsentDays());
        payroll.setTotalAllowances(0.0);
        payroll.setTotalBonuses(0.0);
        payroll.setTotalDeductions(0.0);
        payroll.setStatus(PayrollStatus.DRAFT);
        payroll.setGeneratedBy(generatedBy);
        payroll.setGeneratedAt(LocalDateTime.now());

        BigDecimal grossSalaryBd = payrollCalculationService.calculateGrossSalary(basicSalaryBd, BigDecimal.ZERO, BigDecimal.ZERO);
        payroll.setGrossSalary(grossSalaryBd.doubleValue());

        int unpaidLeaveDays = attendanceSummary.getUnpaidLeaveDays() != null ? attendanceSummary.getUnpaidLeaveDays() : 0;
        int absentDays = attendanceSummary.getAbsentDays() != null ? attendanceSummary.getAbsentDays() : 0;
        int lateDays = attendanceSummary.getLateDays() != null ? attendanceSummary.getLateDays() : 0;
        BigDecimal incomeTaxBd = payrollCalculationService.calculateIncomeTax(grossSalaryBd);
        BigDecimal deductionsBd = payrollCalculationService.calculateDeductions(
                unpaidLeaveDays, absentDays, lateDays, dailySalaryBd, BigDecimal.ZERO, incomeTaxBd);
        payroll.setTotalDeductions(deductionsBd.doubleValue());

        BigDecimal netSalaryBd = payrollCalculationService.calculateNetSalary(grossSalaryBd, deductionsBd);
        payroll.setNetSalary(netSalaryBd.doubleValue());

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

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.GENERATE,
                String.format("Payroll generated for %s — period: %s %s, net: %.2f",
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

        return mapToDto(saved);
    }

    // NOTE: intentionally NOT @Transactional — each employee runs in its own
    // independent REQUIRES_NEW transaction inside payrollGenerationHelper.
    // This prevents a single failure from marking the whole batch as rollback-only.
    public java.util.Map<String, Object> generateBulkPayroll(Long payrollPeriodId, Long generatedBy) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        if (!payrollPeriod.getLocked()) {
            throw new RuntimeException("Payroll period must be locked before generating payroll");
        }

        // Get all active employees (Role.EMPLOYEE only — excluding ADMIN and SUPERADMIN)
        List<User> allUsers = userRepository.findAll();
        List<User> employees = allUsers.stream()
                .filter(u -> u.getRole() != null && u.getRole() == Role.EMPLOYEE)
                .collect(Collectors.toList());

        // Step 1: Generate attendance summaries for all active employees first
        try {
            attendanceService.generateBulkAttendanceSummaries(payrollPeriodId);
        } catch (Exception e) {
            System.err.println("Warning: Bulk attendance summary generation warning: " + e.getMessage());
        }

        int generated = 0;
        int skipped   = 0;
        int failed    = 0;
        StringBuilder details = new StringBuilder();

        // Step 2: Process each employee directly in its own independent transaction
        for (User employee : employees) {
            try {
                boolean created = payrollGenerationHelper.generatePayrollForEmployee(
                        payrollPeriodId, employee.getId(), generatedBy);
                if (created) {
                    generated++;
                    details.append(String.format("Generated for %s (ID: %d). ", employee.getName(), employee.getId()));
                } else {
                    skipped++;
                    details.append(String.format("Skipped for %s (ID: %d) - already exists. ", employee.getName(), employee.getId()));
                }
            } catch (Exception e) {
                failed++;
                details.append(String.format("Failed for %s (ID: %d): %s. ", employee.getName(), employee.getId(), e.getMessage()));
                System.err.println("✗ Failed to generate payroll for employee "
                        + employee.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        String msg = String.format("Bulk payroll complete: %d generated, %d skipped, %d failed.", generated, skipped, failed);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("message", msg);
        response.put("generatedCount", generated);
        response.put("skippedCount", skipped);
        response.put("failedCount", failed);
        response.put("details", details.toString());

        System.out.println(msg + " Details: " + details.toString());
        return response;
    }

    @Transactional
    public PayRollDto approvePayroll(Long payrollId, Long approvedBy) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getStatus() == PayrollStatus.APPROVED || payroll.getStatus() == PayrollStatus.PAID) {
            throw new RuntimeException("Payroll is already approved or paid");
        }

        payroll.setStatus(PayrollStatus.APPROVED);
        payroll.setApprovedBy(approvedBy);
        payroll.setApprovedAt(LocalDateTime.now());

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll for %s %s has been approved. Net salary: %.2f",
                        payroll.getPayrollPeriod().getMonth(), payroll.getPayrollPeriod().getYear(),
                        saved.getNetSalary()),
                "PAYROLL",
                payroll.getUser().getId(),
                saved.getId()
        );

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.APPROVE,
                String.format("Payroll approved for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                approvedBy);

        BigDecimal netSalaryBd = saved.getNetSalary() != null ? BigDecimal.valueOf(saved.getNetSalary()) : BigDecimal.ZERO;
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

        return mapToDto(saved);
    }

    @Transactional
    public PayRollDto markAsPaid(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getStatus() != PayrollStatus.APPROVED) {
            throw new RuntimeException("Payroll must be approved before marking as paid");
        }

        payroll.setStatus(PayrollStatus.PAID);
        payroll.setPaidAt(LocalDateTime.now());

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll for %s %s has been paid. Amount: %.2f",
                        payroll.getPayrollPeriod().getMonth(), payroll.getPayrollPeriod().getYear(),
                        saved.getNetSalary()),
                "PAYROLL",
                payroll.getUser().getId(),
                saved.getId()
        );

        // Audit trail — use the payroll owner as performer since markAsPaid has no explicit userId param
        auditLogService.log("Payroll", saved.getId(), AuditAction.UPDATE,
                String.format("Payroll marked as PAID for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                payroll.getApprovedBy() != null ? payroll.getApprovedBy() : payroll.getGeneratedBy());

        return mapToDto(saved);
    }

    @Transactional
    public PayRollDto regeneratePayroll(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (payroll.getPayrollPeriod().getLocked()) {
            throw new RuntimeException("Payroll period must be unlocked before regenerating payroll");
        }

        if (payroll.getStatus() == PayrollStatus.PAID) {
            throw new RuntimeException("Cannot regenerate paid payroll");
        }

        payrollItemRepository.deleteByPayrollId(payrollId);

        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(payroll.getUser().getId(), payroll.getPayrollPeriod().getId())
                .orElseThrow(() -> new RuntimeException("Attendance summary not found"));

        double basicSalary = payroll.getUser().getBasicSalary() != null ? payroll.getUser().getBasicSalary() : 0.0;
        int workingDays = attendanceSummary.getWorkingDays() != null ? attendanceSummary.getWorkingDays() : 26;

        // FIX (Finding 1): Use calendar days of the month as divisor — consistent
        // with generatePayroll() and PayrollGenerationHelper. Previously this used
        // workingDays (~22) which inflated the daily rate and therefore deductions.
        int daysInMonth = 30;
        try {
            String mStr = payroll.getPayrollPeriod().getMonth();
            if (mStr != null && mStr.contains(" ")) mStr = mStr.split(" ")[0];
            if (mStr != null && payroll.getPayrollPeriod().getYear() != null) {
                java.time.YearMonth ym = java.time.YearMonth.of(
                        payroll.getPayrollPeriod().getYear(),
                        java.time.Month.valueOf(mStr.trim().toUpperCase()));
                daysInMonth = ym.lengthOfMonth();
            }
        } catch (Exception ex) {
            daysInMonth = workingDays > 0 ? workingDays : 30;
        }
        BigDecimal basicSalaryBd = BigDecimal.valueOf(basicSalary);
        BigDecimal dailySalaryBd = payrollCalculationService.calculateDailySalary(basicSalaryBd, daysInMonth);

        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalaryBd.doubleValue());
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(attendanceSummary.getPresentDays());
        payroll.setLateDays(attendanceSummary.getLateDays());
        payroll.setPaidLeaveDays(attendanceSummary.getPaidLeaveDays());
        payroll.setUnpaidLeaveDays(attendanceSummary.getUnpaidLeaveDays());
        payroll.setAbsentDays(attendanceSummary.getAbsentDays());

        BigDecimal grossSalaryBd = payrollCalculationService.calculateGrossSalary(
                basicSalaryBd,
                payroll.getTotalAllowances() != null ? BigDecimal.valueOf(payroll.getTotalAllowances()) : BigDecimal.ZERO,
                payroll.getTotalBonuses() != null ? BigDecimal.valueOf(payroll.getTotalBonuses()) : BigDecimal.ZERO);
        payroll.setGrossSalary(grossSalaryBd.doubleValue());

        int unpaidLeaveDays = attendanceSummary.getUnpaidLeaveDays() != null ? attendanceSummary.getUnpaidLeaveDays() : 0;
        int absentDays = attendanceSummary.getAbsentDays() != null ? attendanceSummary.getAbsentDays() : 0;
        int lateDays = attendanceSummary.getLateDays() != null ? attendanceSummary.getLateDays() : 0;
        BigDecimal incomeTaxBd = payrollCalculationService.calculateIncomeTax(grossSalaryBd);
        BigDecimal deductionsBd = payrollCalculationService.calculateDeductions(
                unpaidLeaveDays, absentDays, lateDays, dailySalaryBd, BigDecimal.ZERO, incomeTaxBd);
        payroll.setTotalDeductions(deductionsBd.doubleValue());

        BigDecimal netSalaryBd = payrollCalculationService.calculateNetSalary(grossSalaryBd, deductionsBd);
        payroll.setNetSalary(netSalaryBd.doubleValue());
        payroll.setStatus(PayrollStatus.DRAFT);

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
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PayRollDto> getPayrollsByPeriod(Long payrollPeriodId) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        return payrollRepository.findByPayrollPeriod(payrollPeriod).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ─── Legacy methods — DEPRECATED ──────────────────────────────────────────
    // These methods do NOT use attendance integration, payroll periods, or policy
    // rules. They produce inaccurate payroll records. Use generatePayroll() or
    // generateBulkPayroll() instead.

    @Deprecated(since = "2024-09", forRemoval = true)
    public void generatePayrollForAllEmployees(int month, int year) {
        List<User> employees = userRepository.findAll();

        for (User employee : employees) {
            Payroll payroll = new Payroll();
            payroll.setUser(employee);
            payroll.setBasicSalary(employee.getBasicSalary());
            payroll.setTotalBonuses(0.0);
            payroll.setTotalDeductions(0.0);
            payroll.setNetSalary(employee.getBasicSalary());
            payroll.setStatus(PayrollStatus.DRAFT);

            Payroll saved = payrollRepository.save(payroll);

            notificationService.createNotification(
                    employee.getId(),
                    String.format("💰 Your payroll has been generated. Net salary: %.2f",
                            employee.getBasicSalary()),
                    "PAYROLL",
                    employee.getId(),
                    saved.getId()
            );

            try {
                String monthStr = String.format("%d-%02d", year, month);
                emailService.sendPayrollNotification(employee.getEmail(), monthStr, year);
                System.out.println("✓ Email sent successfully to: " + employee.getEmail());
            } catch (Exception e) {
                System.err.println("✗ Email failed for: " + employee.getEmail());
                e.printStackTrace();
            }
        }
    }

    @Deprecated(since = "2024-09", forRemoval = true)
    public PayRollDto createPayroll(PayRollDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getUserId()));

        Payroll payroll = new Payroll();
        payroll.setUser(user);
        payroll.setBasicSalary(dto.getSalary() != null ? dto.getSalary() : 0.0);
        payroll.setTotalBonuses(dto.getBonuses() != null ? dto.getBonuses() : 0.0);
        payroll.setTotalDeductions(dto.getDeductions() != null ? dto.getDeductions() : 0.0);
        payroll.setNetSalary(calculateNetSalary(
                payroll.getBasicSalary(),
                payroll.getTotalBonuses(),
                payroll.getTotalDeductions()
        ));
        payroll.setStatus(dto.getStatus() != null ? PayrollStatus.valueOf(dto.getStatus()) : PayrollStatus.DRAFT);

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                user.getId(),
                String.format("💰 Your payroll has been created. Net salary: %.2f",
                        saved.getNetSalary()),
                "PAYROLL",
                user.getId(),
                saved.getId()
        );

        try {
            emailService.sendPayrollNotification(user.getEmail(), dto.getMonth(), Integer.parseInt(dto.getMonth().split("-")[0]));
            System.out.println("✓ Email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("✗ Email failed for: " + user.getEmail());
            e.printStackTrace();
        }

        return mapToDto(saved);
    }

    // ─── Read all paginated (admin/superadmin only — enforced at controller level) ──

    @Transactional(readOnly = true)
    public Page<PayRollDto> getAllPayroll(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        if (search != null && !search.trim().isEmpty()) {
            return payrollRepository.searchPayrolls(search.trim(), pageable).map(this::mapToDto);
        }
        return payrollRepository.findAll(pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<PayRollDto> getAllPayroll(int page, int size) {
        return getAllPayroll(page, size, null);
    }


    // ─── Read by ID — now with ownership + status enforcement ─────────────────

    /**
     * @param requestingUserId the ID of the authenticated user making the request (from JWT)
     * @param isPrivileged     true if the requester is ADMIN or SUPERADMIN
     */
    @Transactional(readOnly = true)
    public PayRollDto getPayrollById(long id, Long requestingUserId, boolean isPrivileged) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));

        if (!isPrivileged) {
            if (requestingUserId == null || !payroll.getUser().getId().equals(requestingUserId)) {
                throw new AccessDeniedException("You are not authorized to view this payroll record");
            }
            if (payroll.getStatus() != PayrollStatus.APPROVED && payroll.getStatus() != PayrollStatus.PAID) {
                throw new AccessDeniedException("This payroll is not yet available for viewing");
            }
        }

        return mapToDto(payroll);
    }

    // ─── Read by user ID paginated — now with ownership enforcement ───────────

    @Transactional(readOnly = true)
    public Page<PayRollDto> getPayrollByUserId(Long userId, int page, int size, Long requestingUserId, boolean isPrivileged) {
        if (!isPrivileged) {
            if (requestingUserId == null || !userId.equals(requestingUserId)) {
                throw new AccessDeniedException("You are not authorized to view these payroll records");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<PayRollDto> result = payrollRepository.findByUserId(userId, pageable).map(this::mapToDto);

        if (!isPrivileged) {
            // Employees only ever see APPROVED/PAID payslips, never DRAFT/REVIEWED/ARCHIVED
            List<PayRollDto> filtered = result.getContent().stream()
                    .filter(dto -> "APPROVED".equals(dto.getStatus()) || "PAID".equals(dto.getStatus()))
                    .collect(Collectors.toList());
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        return result;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public PayRollDto updatePayroll(Long id, PayRollDto dto) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));

        // Snapshot old values for audit trail
        double oldBonuses    = payroll.getTotalBonuses()    != null ? payroll.getTotalBonuses()    : 0.0;
        double oldAllowances = payroll.getTotalAllowances() != null ? payroll.getTotalAllowances() : 0.0;
        double oldNet        = payroll.getNetSalary()       != null ? payroll.getNetSalary()       : 0.0;

        if (dto.getTotalBonuses() != null)    payroll.setTotalBonuses(dto.getTotalBonuses());
        else if (dto.getBonuses() != null)    payroll.setTotalBonuses(dto.getBonuses());

        if (dto.getTotalAllowances() != null) payroll.setTotalAllowances(dto.getTotalAllowances());

        if (dto.getStatus() != null) {
            payroll.setStatus(PayrollStatus.valueOf(dto.getStatus()));
        }

        BigDecimal basicBd = payroll.getBasicSalary() != null ? BigDecimal.valueOf(payroll.getBasicSalary()) : BigDecimal.ZERO;
        BigDecimal allowancesBd = payroll.getTotalAllowances() != null ? BigDecimal.valueOf(payroll.getTotalAllowances()) : BigDecimal.ZERO;
        BigDecimal bonusesBd = payroll.getTotalBonuses() != null ? BigDecimal.valueOf(payroll.getTotalBonuses()) : BigDecimal.ZERO;
        BigDecimal grossSalaryBd = payrollCalculationService.calculateGrossSalary(basicBd, allowancesBd, bonusesBd);
        payroll.setGrossSalary(grossSalaryBd.doubleValue());

        BigDecimal dailySalaryBd = payroll.getDailySalary() != null ? BigDecimal.valueOf(payroll.getDailySalary()) : BigDecimal.ZERO;
        int unpaidLeave = payroll.getUnpaidLeaveDays() != null ? payroll.getUnpaidLeaveDays() : 0;
        int absentDays  = payroll.getAbsentDays()      != null ? payroll.getAbsentDays()      : 0;
        int lateDays    = payroll.getLateDays()         != null ? payroll.getLateDays()         : 0;
        BigDecimal autoDeductionsBd = payrollCalculationService.calculateDeductions(
                unpaidLeave, absentDays, lateDays, dailySalaryBd, BigDecimal.ZERO);

        BigDecimal totalDeductionsBd;
        if (dto.getDeductions() != null) {
            totalDeductionsBd = BigDecimal.valueOf(dto.getDeductions());
        } else {
            totalDeductionsBd = autoDeductionsBd;
        }
        payroll.setTotalDeductions(totalDeductionsBd.doubleValue());

        BigDecimal netSalaryBd = payrollCalculationService.calculateNetSalary(grossSalaryBd, totalDeductionsBd);
        payroll.setNetSalary(netSalaryBd.doubleValue());

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll has been updated. Net salary: %.2f",
                        saved.getNetSalary()),
                "PAYROLL",
                payroll.getUser().getId(),
                saved.getId()
        );

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.UPDATE,
                String.format("Payroll updated for %s — bonuses: %.2f→%.2f, allowances: %.2f→%.2f, net: %.2f→%.2f",
                        payroll.getUser().getName(), oldBonuses,
                        payroll.getTotalBonuses() != null ? payroll.getTotalBonuses() : 0.0,
                        oldAllowances,
                        payroll.getTotalAllowances() != null ? payroll.getTotalAllowances() : 0.0,
                        oldNet, saved.getNetSalary()),
                payroll.getGeneratedBy() != null ? payroll.getGeneratedBy() : payroll.getUser().getId());

        return mapToDto(saved);
    }

    // ─── Bulk Delete ──────────────────────────────────────────────────────────

    @Transactional
    public void deleteBulkPayroll(List<Long> ids) {
        for (Long id : ids) {
            Payroll payroll = payrollRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Payroll not found: " + id));
            if (payroll.getStatus() == PayrollStatus.PAID) {
                throw new RuntimeException("Cannot delete paid payroll (ID: " + id + ")");
            }
            payrollRepository.deleteById(id);
        }
    }

    // ─── Bulk Approve ─────────────────────────────────────────────────────────

    // NOTE: intentionally NOT @Transactional — each employee approval runs in its own
    // independent REQUIRES_NEW transaction inside payrollGenerationHelper.
    public List<PayRollDto> approveBulkPayroll(List<Long> ids, Long approvedBy) {
        List<PayRollDto> results = new java.util.ArrayList<>();
        for (Long id : ids) {
            try {
                Payroll approved = payrollGenerationHelper.approvePayrollForEmployee(id, approvedBy);
                results.add(mapToDto(approved));
            } catch (Exception e) {
                System.err.println("✗ Failed to approve payroll ID " + id + ": " + e.getMessage());
            }
        }
        return results;
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    public void deletePayroll(Long id) {
        if (!payrollRepository.existsById(id)) {
            throw new RuntimeException("Payroll not found for ID: " + id);
        }
        payrollRepository.deleteById(id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public double calculateNetSalary(Double salary, Double bonuses, Double deductions) {
        double sal = salary != null ? salary : 0.0;
        double bon = bonuses != null ? bonuses : 0.0;
        double ded = deductions != null ? deductions : 0.0;
        return sal + bon - ded;
    }

    public PayRollDto mapToDto(Payroll payroll) {
        PayRollDto dto = new PayRollDto();
        dto.setId(payroll.getId());
        dto.setUserId(payroll.getUser().getId());
        dto.setUserName(payroll.getUser().getName());
        dto.setSalary(payroll.getBasicSalary());
        dto.setBonuses(payroll.getTotalBonuses());
        dto.setDeductions(payroll.getTotalDeductions());
        dto.setNetSalary(payroll.getNetSalary());
        dto.setStatus(payroll.getStatus() != null ? payroll.getStatus().name() : null);
        if (payroll.getPayrollPeriod() != null) {
            dto.setMonth(payroll.getPayrollPeriod().getMonth() + " " + payroll.getPayrollPeriod().getYear());
        }

        // Map breakdown fields
        dto.setBasicSalary(payroll.getBasicSalary());
        dto.setDailySalary(payroll.getDailySalary());
        dto.setWorkingDays(payroll.getWorkingDays());
        dto.setPresentDays(payroll.getPresentDays());
        dto.setLateDays(payroll.getLateDays());
        dto.setPaidLeaveDays(payroll.getPaidLeaveDays());
        dto.setUnpaidLeaveDays(payroll.getUnpaidLeaveDays());
        dto.setAbsentDays(payroll.getAbsentDays());
        dto.setTotalAllowances(payroll.getTotalAllowances());
        dto.setTotalBonuses(payroll.getTotalBonuses());
        dto.setTotalDeductions(payroll.getTotalDeductions());

        // Calculate itemized deductions for breakdown view
        BigDecimal dailySalaryBd = payroll.getDailySalary() != null ? BigDecimal.valueOf(payroll.getDailySalary()) : BigDecimal.ZERO;
        int lateDays = payroll.getLateDays() != null ? payroll.getLateDays() : 0;
        int unpaidLeave = payroll.getUnpaidLeaveDays() != null ? payroll.getUnpaidLeaveDays() : 0;
        int absentDays = payroll.getAbsentDays() != null ? payroll.getAbsentDays() : 0;
        BigDecimal grossSalaryBd = payroll.getGrossSalary() != null ? BigDecimal.valueOf(payroll.getGrossSalary()) : BigDecimal.ZERO;

        BigDecimal lateDedBd = payrollCalculationService.applyLatePolicy(lateDays, dailySalaryBd);
        BigDecimal unpaidDedBd = payrollCalculationService.applyUnpaidLeavePolicy(unpaidLeave, dailySalaryBd);
        BigDecimal absentDedBd = payrollCalculationService.applyAbsentPolicy(absentDays, dailySalaryBd);

        Double taxDed = null;
        if (payroll.getPayrollItems() != null && !payroll.getPayrollItems().isEmpty()) {
            for (PayrollItem item : payroll.getPayrollItems()) {
                if (item.getType() == PayrollItemType.DEDUCTION && item.getName() != null && item.getName().toLowerCase().contains("tax")) {
                    taxDed = item.getAmount();
                    break;
                }
            }
        }
        if (taxDed == null) {
            taxDed = payrollCalculationService.calculateIncomeTax(grossSalaryBd).doubleValue();
        }

        dto.setLateDeduction(lateDedBd.doubleValue());
        dto.setFbrTaxDeduction(taxDed);
        dto.setUnpaidLeaveDeduction(unpaidDedBd.doubleValue());
        dto.setAbsentDeduction(absentDedBd.doubleValue());

        dto.setGrossSalary(payroll.getGrossSalary());
        dto.setGeneratedBy(payroll.getGeneratedBy());
        dto.setGeneratedAt(payroll.getGeneratedAt());
        dto.setApprovedBy(payroll.getApprovedBy());
        dto.setApprovedAt(payroll.getApprovedAt());
        dto.setPaidAt(payroll.getPaidAt());
        return dto;
    }
}