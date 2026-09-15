package com.hrm.system.controller;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.service.PayRollService;
import com.hrm.system.service.PayrollOtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayRollService payRollService;

    @Autowired
    private PayrollOtpService payrollOtpService;

    private Long getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    private boolean isPrivileged(HttpServletRequest request) {
        return request.isUserInRole("ADMIN") || request.isUserInRole("SUPERADMIN");
    }

    private void verifyElevatedAccess(HttpServletRequest request) {
        if (isPrivileged(request)) {
            Long userId = getRequestingUserId(request);
            String token = request.getHeader("X-Payroll-Elevated-Token");
            if (!payrollOtpService.isElevatedAccessValid(userId, token)) {
                throw new AccessDeniedException("Elevated payroll access required. Please complete email OTP verification to access payroll processing.");
            }
        }
    }

    // ─── New payroll generation endpoints ─────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> generatePayroll(
            @RequestParam Long payrollPeriodId,
            @RequestParam Long employeeId,
            @RequestParam Long generatedBy,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        PayRollDto generated = payRollService.generatePayroll(payrollPeriodId, employeeId, generatedBy);
        return new ResponseEntity<>(generated, HttpStatus.CREATED);
    }

    @PostMapping("/generate/bulk")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> generateBulkPayroll(
            @RequestParam Long payrollPeriodId,
            @RequestParam Long generatedBy,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        java.util.Map<String, Object> result = payRollService.generateBulkPayroll(payrollPeriodId, generatedBy);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> approvePayroll(
            @PathVariable Long id,
            @RequestParam Long approvedBy,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        PayRollDto approved = payRollService.approvePayroll(id, approvedBy);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> markAsPaid(@PathVariable Long id, HttpServletRequest request) {
        verifyElevatedAccess(request);
        PayRollDto paid = payRollService.markAsPaid(id);
        return ResponseEntity.ok(paid);
    }

    @PutMapping("/{id}/regenerate")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> regeneratePayroll(@PathVariable Long id, HttpServletRequest request) {
        verifyElevatedAccess(request);
        PayRollDto regenerated = payRollService.regeneratePayroll(id);
        return ResponseEntity.ok(regenerated);
    }

    @GetMapping("/period/{periodId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<PayRollDto>> getPayrollsByPeriod(@PathVariable Long periodId, HttpServletRequest request) {
        verifyElevatedAccess(request);
        List<PayRollDto> payrolls = payRollService.getPayrollsByPeriod(periodId);
        return ResponseEntity.ok(payrolls);
    }

    // ─── Legacy endpoints ─────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> createPayroll(@RequestBody PayRollDto dto, HttpServletRequest request) {
        verifyElevatedAccess(request);
        PayRollDto created = payRollService.createPayroll(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Page<PayRollDto>> getAllPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        return ResponseEntity.ok(payRollService.getAllPayroll(page, size, search));
    }


    // ─── Ownership-enforced endpoints ──────────────────────────────────────────

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Page<PayRollDto>> getPayrollByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long requestingUserId = getRequestingUserId(request);
        boolean privileged = isPrivileged(request);
        return ResponseEntity.ok(payRollService.getPayrollByUserId(userId, page, size, requestingUserId, privileged));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<PayRollDto> getPayrollById(@PathVariable Long id, HttpServletRequest request) {
        Long requestingUserId = getRequestingUserId(request);
        boolean privileged = isPrivileged(request);
        return ResponseEntity.ok(payRollService.getPayrollById(id, requestingUserId, privileged));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> updatePayroll(@PathVariable Long id, @RequestBody PayRollDto dto, HttpServletRequest request) {
        verifyElevatedAccess(request);
        return ResponseEntity.ok(payRollService.updatePayroll(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<String> deletePayroll(@PathVariable Long id, HttpServletRequest request) {
        verifyElevatedAccess(request);
        payRollService.deletePayroll(id);
        return ResponseEntity.ok("Payroll record Deleted successfully");
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<String> deleteBulkPayroll(@RequestBody java.util.List<Long> ids, HttpServletRequest request) {
        verifyElevatedAccess(request);
        int deleted = payRollService.deleteBulkPayroll(ids);
        int skipped = ids.size() - deleted;
        String msg = "Deleted " + deleted + " payroll record(s) successfully.";
        if (skipped > 0) {
            msg += " " + skipped + " PAID record(s) were skipped (paid payrolls cannot be deleted).";
        }
        return ResponseEntity.ok(msg);
    }

    @PutMapping("/bulk-approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<java.util.List<PayRollDto>> approveBulkPayroll(
            @RequestBody java.util.List<Long> ids,
            @RequestParam Long approvedBy,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        return ResponseEntity.ok(payRollService.approveBulkPayroll(ids, approvedBy));
    }

    @PutMapping("/bulk-pay")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<java.util.List<PayRollDto>> payBulkPayroll(
            @RequestBody java.util.List<Long> ids,
            HttpServletRequest request) {
        verifyElevatedAccess(request);
        return ResponseEntity.ok(payRollService.payBulkPayroll(ids));
    }
}