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

        // Always refresh the summary so newly marked absences are picked up
        attendanceService.generateAttendanceSummary(employeeId, payrollPeriodId);
        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Failed to generate attendance summary for employee: " + employeeId));

        double basicSalary = employee.getBasicSalary() != null ? employee.getBasicSalary() : 0.0;
        int workingDays = attendanceSummary.getWorkingDays() != null ? attendanceSummary.getWorkingDays() : 26;

        // Daily rate is per working day, the same unit absences are counted in
        BigDecimal basicSalaryBd = BigDecimal.valueOf(basicSalary);
        BigDecimal dailySalaryBd = payrollCalculationService.calculateDailySalary(basicSalaryBd, workingDays > 0 ? workingDays : 26);

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
        payrollGenerationHelper.syncIncomeTaxItem(saved, incomeTaxBd);

        // No employee notification here — employees are only notified when payroll is PAID.

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.GENERATE,
                String.format("Payroll generated for %s — period: %s %s, net: %.2f",
                        employee.getName(), payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalaryBd.doubleValue()),
                generatedBy);

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

        // No employee notification on approval — employees are only notified when payroll is PAID.

        // Audit trail
        auditLogService.log("Payroll", saved.getId(), AuditAction.APPROVE,
                String.format("Payroll approved for %s — net: %.2f",
                        payroll.getUser().getName(), saved.getNetSalary()),
                approvedBy);

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

        // Email the employee ONLY now that the payroll is PAID (sent AFTER_COMMIT, async)
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
        if (payroll.getPayrollItems() != null) payroll.getPayrollItems().clear();

        // Refresh the summary so newly marked absences are picked up
        Long employeeId = payroll.getUser().getId();
        Long periodId = payroll.getPayrollPeriod().getId();
        attendanceService.generateAttendanceSummary(employeeId, periodId);
        AttendanceSummary attendanceSummary = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, periodId)
                .orElseThrow(() -> new RuntimeException("Attendance summary not found"));

        double basicSalary = payroll.getUser().getBasicSalary() != null ? payroll.getUser().getBasicSalary() : 0.0;
        int workingDays = attendanceSummary.getWorkingDays() != null ? attendanceSummary.getWorkingDays() : 26;

        // Daily rate is per working day, the same unit absences are counted in
        BigDecimal dailySalaryBd = payrollCalculationService.calculateDailySalary(
                BigDecimal.valueOf(basicSalary), workingDays > 0 ? workingDays : 26);

        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalaryBd.doubleValue());
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(attendanceSummary.getPresentDays());
        payroll.setLateDays(attendanceSummary.getLateDays());
        payroll.setPaidLeaveDays(attendanceSummary.getPaidLeaveDays());
        payroll.setUnpaidLeaveDays(attendanceSummary.getUnpaidLeaveDays());
        payroll.setAbsentDays(attendanceSummary.getAbsentDays());
        payroll.setHolidayDays(attendanceSummary.getHolidayDays() != null ? attendanceSummary.getHolidayDays() : 0);
        payroll.setStatus(PayrollStatus.DRAFT);

        // Bonuses, allowances, manual deduction and any FBR tax override set by HR are kept
        BigDecimal incomeTaxBd = payrollCalculationService.recalculateTotals(payroll);

        Payroll saved = payrollRepository.save(payroll);
        payrollGenerationHelper.syncIncomeTaxItem(saved, incomeTaxBd);
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

            // No email here — employees are emailed only when payroll is marked PAID.
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

        // No email here — employees are emailed only when payroll is marked PAID.

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

    /**
     * HR edit of a payroll. Attendance days, bonuses, allowances, a manual
     * deduction and the FBR tax (override or back to automatic) can be changed
     * while the payroll is DRAFT; totals are then recalculated server-side.
     * A status-only update (no amount fields) keeps working for any status.
     */
    @Transactional
    public PayRollDto updatePayroll(Long id, PayRollDto dto, Long performedBy) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));

        boolean editsAmounts = dto.getTotalBonuses() != null || dto.getBonuses() != null
                || dto.getTotalAllowances() != null || dto.getManualDeduction() != null
                || dto.getFbrTaxOverride() != null || Boolean.TRUE.equals(dto.getUseAutoFbrTax())
                || dto.getPresentDays() != null || dto.getAbsentDays() != null
                || dto.getUnpaidLeaveDays() != null || dto.getLateDays() != null
                || dto.getPaidLeaveDays() != null;

        if (editsAmounts && payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT payrolls can be edited. This payroll is " + payroll.getStatus() + ".");
        }

        List<String> changes = new java.util.ArrayList<>();
        double oldNet = payroll.getNetSalary() != null ? payroll.getNetSalary() : 0.0;

        Double bonuses = dto.getTotalBonuses() != null ? dto.getTotalBonuses() : dto.getBonuses();
        if (bonuses != null) {
            requireNonNegative(bonuses, "Bonus");
            trackChange(changes, "bonus", payroll.getTotalBonuses(), bonuses);
            payroll.setTotalBonuses(bonuses);
        }
        if (dto.getTotalAllowances() != null) {
            requireNonNegative(dto.getTotalAllowances(), "Allowances");
            trackChange(changes, "allowances", payroll.getTotalAllowances(), dto.getTotalAllowances());
            payroll.setTotalAllowances(dto.getTotalAllowances());
        }
        if (dto.getManualDeduction() != null) {
            requireNonNegative(dto.getManualDeduction(), "Manual deduction");
            trackChange(changes, "manual deduction", payroll.getManualDeduction(), dto.getManualDeduction());
            payroll.setManualDeduction(dto.getManualDeduction());
        }
        if (Boolean.TRUE.equals(dto.getUseAutoFbrTax())) {
            if (payroll.getFbrTaxOverride() != null) changes.add("FBR tax: override removed (automatic)");
            payroll.setFbrTaxOverride(null);
        } else if (dto.getFbrTaxOverride() != null) {
            requireNonNegative(dto.getFbrTaxOverride(), "FBR tax");
            trackChange(changes, "FBR tax", payroll.getFbrTaxOverride(), dto.getFbrTaxOverride());
            payroll.setFbrTaxOverride(dto.getFbrTaxOverride());
        }

        if (dto.getPresentDays() != null)     { trackChange(changes, "present days", payroll.getPresentDays(), dto.getPresentDays());          payroll.setPresentDays(requireDays(dto.getPresentDays(), "Present days")); }
        if (dto.getAbsentDays() != null)      { trackChange(changes, "absent days", payroll.getAbsentDays(), dto.getAbsentDays());             payroll.setAbsentDays(requireDays(dto.getAbsentDays(), "Absent days")); }
        if (dto.getUnpaidLeaveDays() != null) { trackChange(changes, "unpaid leave days", payroll.getUnpaidLeaveDays(), dto.getUnpaidLeaveDays()); payroll.setUnpaidLeaveDays(requireDays(dto.getUnpaidLeaveDays(), "Unpaid leave days")); }
        if (dto.getPaidLeaveDays() != null)   { trackChange(changes, "paid leave days", payroll.getPaidLeaveDays(), dto.getPaidLeaveDays());   payroll.setPaidLeaveDays(requireDays(dto.getPaidLeaveDays(), "Paid leave days")); }
        if (dto.getLateDays() != null)        { trackChange(changes, "late days", payroll.getLateDays(), dto.getLateDays());                   payroll.setLateDays(requireDays(dto.getLateDays(), "Late days")); }

        int workingDays = payroll.getWorkingDays() != null ? payroll.getWorkingDays() : 0;
        int deductedDays = (payroll.getAbsentDays() != null ? payroll.getAbsentDays() : 0)
                + (payroll.getUnpaidLeaveDays() != null ? payroll.getUnpaidLeaveDays() : 0);
        if (workingDays > 0 && deductedDays > workingDays) {
            throw new IllegalArgumentException("Absent + unpaid leave days (" + deductedDays
                    + ") cannot exceed working days (" + workingDays + ").");
        }

        if (dto.getDeductionNote() != null) {
            String note = dto.getDeductionNote().trim();
            payroll.setDeductionNote(note.isEmpty() ? null : note.substring(0, Math.min(note.length(), 500)));
        }

        if (dto.getStatus() != null) {
            payroll.setStatus(PayrollStatus.valueOf(dto.getStatus()));
        }

        BigDecimal incomeTaxBd = payrollCalculationService.recalculateTotals(payroll);
        Payroll saved = payrollRepository.save(payroll);
        payrollGenerationHelper.syncIncomeTaxItem(saved, incomeTaxBd);

        // No employee notification on edits — employees are only notified when payroll is PAID.

        if (editsAmounts) {
            String summary = changes.isEmpty() ? "no field changes" : String.join(", ", changes);
            auditLogService.log("Payroll", saved.getId(), AuditAction.UPDATE,
                    String.format("Payroll edited for %s — %s; net: %.2f→%.2f%s",
                            payroll.getUser().getName(), summary, oldNet, saved.getNetSalary(),
                            saved.getDeductionNote() != null ? " — note: " + saved.getDeductionNote() : ""),
                    performedBy != null ? performedBy : payroll.getGeneratedBy());
        }

        return mapToDto(saved);
    }

    private static void requireNonNegative(Double value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " cannot be negative.");
    }

    private static int requireDays(Integer value, String field) {
        if (value < 0 || value > 31) throw new IllegalArgumentException(field + " must be between 0 and 31.");
        return value;
    }

    private static void trackChange(List<String> changes, String field, Number oldValue, Number newValue) {
        double before = oldValue != null ? oldValue.doubleValue() : 0.0;
        if (Double.compare(before, newValue.doubleValue()) != 0) {
            changes.add(String.format("%s: %s→%s", field, formatNumber(before), formatNumber(newValue.doubleValue())));
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    // ─── Bulk Delete ──────────────────────────────────────────────────────────

    /**
     * Bulk-delete payrolls by IDs.
     * PAID payrolls are silently skipped (they are financial records; deleting them
     * would break audit trails). Returns the number of records actually deleted.
     */
    @Transactional
    public int deleteBulkPayroll(List<Long> ids) {
        int deleted = 0;
        for (Long id : ids) {
            Payroll payroll = payrollRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Payroll not found: " + id));
            if (payroll.getStatus() == PayrollStatus.PAID) {
                // Skip PAID payrolls — they are immutable financial records.
                continue;
            }
            payrollRepository.deleteById(id);
            deleted++;
        }
        return deleted;
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

    // ─── Bulk Pay ─────────────────────────────────────────────────────────────

    public List<PayRollDto> payBulkPayroll(List<Long> ids) {
        List<PayRollDto> results = new java.util.ArrayList<>();
        for (Long id : ids) {
            try {
                Payroll paid = payrollGenerationHelper.payPayrollForEmployee(id);
                results.add(mapToDto(paid));
            } catch (Exception e) {
                System.err.println("✗ Failed to mark payroll ID " + id + " as paid: " + e.getMessage());
            }
        }
        return results;
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Delete a single payroll record.
     * PAID payrolls cannot be deleted — they are immutable financial records.
     */
    public void deletePayroll(Long id) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));
        if (payroll.getStatus() == PayrollStatus.PAID) {
            throw new RuntimeException("Cannot delete a PAID payroll record (ID: " + id + "). Paid payrolls are permanent financial records.");
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
        dto.setHolidayDays(payroll.getHolidayDays() != null ? payroll.getHolidayDays() : 0);
        dto.setTotalAllowances(payroll.getTotalAllowances());
        dto.setTotalBonuses(payroll.getTotalBonuses());
        dto.setTotalDeductions(payroll.getTotalDeductions());

        // Calculate itemized deductions for breakdown view
        BigDecimal dailySalaryBd = payroll.getDailySalary() != null ? BigDecimal.valueOf(payroll.getDailySalary()) : BigDecimal.ZERO;
        int lateDays = payroll.getLateDays() != null ? payroll.getLateDays() : 0;
        int unpaidLeave = payroll.getUnpaidLeaveDays() != null ? payroll.getUnpaidLeaveDays() : 0;
        int absentDays = payroll.getAbsentDays() != null ? payroll.getAbsentDays() : 0;
        BigDecimal basicSalaryBd = payroll.getBasicSalary() != null ? BigDecimal.valueOf(payroll.getBasicSalary()) : BigDecimal.ZERO;

        BigDecimal lateDedBd = payrollCalculationService.applyLatePolicy(lateDays, dailySalaryBd);
        BigDecimal unpaidDedBd = payrollCalculationService.applyUnpaidLeavePolicy(unpaidLeave, dailySalaryBd);
        BigDecimal absentDedBd = payrollCalculationService.applyAbsentPolicy(absentDays, dailySalaryBd);
        // FBR tax is calculated on the basic salary
        BigDecimal autoTaxBd = payrollCalculationService.calculateIncomeTax(basicSalaryBd);

        Double taxDed = payroll.getFbrTaxOverride();
        if (taxDed == null && payroll.getPayrollItems() != null && !payroll.getPayrollItems().isEmpty()) {
            for (PayrollItem item : payroll.getPayrollItems()) {
                if (item.getType() == PayrollItemType.DEDUCTION && item.getName() != null && item.getName().toLowerCase().contains("tax")) {
                    taxDed = item.getAmount();
                    break;
                }
            }
        }
        if (taxDed == null) {
            taxDed = autoTaxBd.doubleValue();
        }

        dto.setLateDeduction(lateDedBd.doubleValue());
        dto.setFbrTaxDeduction(taxDed);
        dto.setAutoFbrTax(autoTaxBd.doubleValue());
        dto.setFbrTaxOverride(payroll.getFbrTaxOverride());
        dto.setManualDeduction(payroll.getManualDeduction() != null ? payroll.getManualDeduction() : 0.0);
        dto.setDeductionNote(payroll.getDeductionNote());
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