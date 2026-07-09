package com.hrm.system.service;

import com.hrm.system.dto.AttendanceSummaryDto;
import com.hrm.system.dto.PayRollDto;
import com.hrm.system.model.*;
import com.hrm.system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayRollService {

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
        double dailySalary = payrollCalculationService.calculateDailySalary(basicSalary, workingDays);

        Payroll payroll = new Payroll();
        payroll.setPayrollPeriod(payrollPeriod);
        payroll.setUser(employee);
        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalary);
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

        int presentDays = attendanceSummary.getPresentDays() != null ? attendanceSummary.getPresentDays() : 0;
        int paidLeaveDays = attendanceSummary.getPaidLeaveDays() != null ? attendanceSummary.getPaidLeaveDays() : 0;
        double grossSalary = payrollCalculationService.calculateGrossSalary(
                basicSalary, presentDays, paidLeaveDays, 0.0, 0.0);
        payroll.setGrossSalary(grossSalary);

        int unpaidLeaveDays = attendanceSummary.getUnpaidLeaveDays() != null ? attendanceSummary.getUnpaidLeaveDays() : 0;
        int absentDays = attendanceSummary.getAbsentDays() != null ? attendanceSummary.getAbsentDays() : 0;
        int lateDays = attendanceSummary.getLateDays() != null ? attendanceSummary.getLateDays() : 0;
        double deductions = payrollCalculationService.calculateDeductions(
                unpaidLeaveDays, absentDays, lateDays, dailySalary, 0.0);
        payroll.setTotalDeductions(deductions);

        double netSalary = payrollCalculationService.calculateNetSalary(grossSalary, deductions);
        payroll.setNetSalary(netSalary);

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                employee.getId(),
                String.format("💰 Your payroll for %s %s has been generated. Net salary: %.2f",
                        payrollPeriod.getMonth(), payrollPeriod.getYear(), netSalary),
                "PAYROLL",
                employee.getId(),
                saved.getId()
        );

        return mapToDto(saved);
    }

    @Transactional
    public void generateBulkPayroll(Long payrollPeriodId, Long generatedBy) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        if (!payrollPeriod.getLocked()) {
            throw new RuntimeException("Payroll period must be locked before generating payroll");
        }

        // Generate attendance summaries for all active employees first
        attendanceService.generateBulkAttendanceSummaries(payrollPeriodId);

        List<AttendanceSummary> summaries = attendanceSummaryRepository.findAll().stream()
                .filter(s -> s.getPayrollPeriod().getId().equals(payrollPeriodId))
                .collect(Collectors.toList());

        for (AttendanceSummary summary : summaries) {
            try {
                generatePayroll(payrollPeriodId, summary.getEmployee().getId(), generatedBy);
            } catch (Exception e) {
                System.err.println("Failed to generate payroll for employee: " + summary.getEmployee().getId());
                e.printStackTrace();
            }
        }
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
        double dailySalary = payrollCalculationService.calculateDailySalary(basicSalary, workingDays);

        payroll.setBasicSalary(basicSalary);
        payroll.setDailySalary(dailySalary);
        payroll.setWorkingDays(workingDays);
        payroll.setPresentDays(attendanceSummary.getPresentDays());
        payroll.setLateDays(attendanceSummary.getLateDays());
        payroll.setPaidLeaveDays(attendanceSummary.getPaidLeaveDays());
        payroll.setUnpaidLeaveDays(attendanceSummary.getUnpaidLeaveDays());
        payroll.setAbsentDays(attendanceSummary.getAbsentDays());

        int presentDays = attendanceSummary.getPresentDays() != null ? attendanceSummary.getPresentDays() : 0;
        int paidLeaveDays = attendanceSummary.getPaidLeaveDays() != null ? attendanceSummary.getPaidLeaveDays() : 0;
        double grossSalary = payrollCalculationService.calculateGrossSalary(
                basicSalary, presentDays, paidLeaveDays, payroll.getTotalAllowances(), payroll.getTotalBonuses());
        payroll.setGrossSalary(grossSalary);

        int unpaidLeaveDays = attendanceSummary.getUnpaidLeaveDays() != null ? attendanceSummary.getUnpaidLeaveDays() : 0;
        int absentDays = attendanceSummary.getAbsentDays() != null ? attendanceSummary.getAbsentDays() : 0;
        int lateDays = attendanceSummary.getLateDays() != null ? attendanceSummary.getLateDays() : 0;
        double deductions = payrollCalculationService.calculateDeductions(
                unpaidLeaveDays, absentDays, lateDays, dailySalary, 0.0);
        payroll.setTotalDeductions(deductions);

        double netSalary = payrollCalculationService.calculateNetSalary(grossSalary, deductions);
        payroll.setNetSalary(netSalary);
        payroll.setStatus(PayrollStatus.DRAFT);

        Payroll saved = payrollRepository.save(payroll);
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

    // ─── Legacy methods (keep for backward compatibility) ─────────────────────

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
    public Page<PayRollDto> getAllPayroll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return payrollRepository.findAll(pageable).map(this::mapToDto);
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

        payroll.setBasicSalary(dto.getSalary() != null ? dto.getSalary() : 0.0);
        payroll.setTotalBonuses(dto.getBonuses() != null ? dto.getBonuses() : 0.0);
        payroll.setTotalDeductions(dto.getDeductions() != null ? dto.getDeductions() : 0.0);
        payroll.setNetSalary(calculateNetSalary(
                payroll.getBasicSalary(),
                payroll.getTotalBonuses(),
                payroll.getTotalDeductions()
        ));
        if (dto.getStatus() != null) {
            payroll.setStatus(PayrollStatus.valueOf(dto.getStatus()));
        }

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll has been updated. Net salary: %.2f",
                        saved.getNetSalary()),
                "PAYROLL",
                payroll.getUser().getId(),
                saved.getId()
        );

        return mapToDto(saved);
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
        dto.setGrossSalary(payroll.getGrossSalary());
        dto.setGeneratedBy(payroll.getGeneratedBy());
        dto.setGeneratedAt(payroll.getGeneratedAt());
        dto.setApprovedBy(payroll.getApprovedBy());
        dto.setApprovedAt(payroll.getApprovedAt());
        dto.setPaidAt(payroll.getPaidAt());
        return dto;
    }
}