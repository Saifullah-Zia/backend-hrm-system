package com.hrm.system.controller;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.service.PayRollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayRollService payRollService;

    // ─── New payroll generation endpoints ─────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> generatePayroll(
            @RequestParam Long payrollPeriodId,
            @RequestParam Long employeeId,
            @RequestParam Long generatedBy) {
        PayRollDto generated = payRollService.generatePayroll(payrollPeriodId, employeeId, generatedBy);
        return new ResponseEntity<>(generated, HttpStatus.CREATED);
    }

    @PostMapping("/generate/bulk")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<String> generateBulkPayroll(
            @RequestParam Long payrollPeriodId,
            @RequestParam Long generatedBy) {
        payRollService.generateBulkPayroll(payrollPeriodId, generatedBy);
        return ResponseEntity.ok("Bulk payroll generation initiated");
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> approvePayroll(
            @PathVariable Long id,
            @RequestParam Long approvedBy) {
        PayRollDto approved = payRollService.approvePayroll(id, approvedBy);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> markAsPaid(@PathVariable Long id) {
        PayRollDto paid = payRollService.markAsPaid(id);
        return ResponseEntity.ok(paid);
    }

    @PutMapping("/{id}/regenerate")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> regeneratePayroll(@PathVariable Long id) {
        PayRollDto regenerated = payRollService.regeneratePayroll(id);
        return ResponseEntity.ok(regenerated);
    }

    @GetMapping("/period/{periodId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<PayRollDto>> getPayrollsByPeriod(@PathVariable Long periodId) {
        List<PayRollDto> payrolls = payRollService.getPayrollsByPeriod(periodId);
        return ResponseEntity.ok(payrolls);
    }

    // ─── Legacy endpoints (keep for backward compatibility) ───────────────────

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> createPayroll(@RequestBody PayRollDto dto) {
        PayRollDto created = payRollService.createPayroll(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Page<PayRollDto>> getAllPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(payRollService.getAllPayroll(page, size));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Page<PayRollDto>> getPayrollByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(payRollService.getPayrollByUserId(userId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<PayRollDto> getPayrollById(@PathVariable Long id) {
        return ResponseEntity.ok(payRollService.getPayrollById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> updatePayroll(@PathVariable Long id, @RequestBody PayRollDto dto) {
        return ResponseEntity.ok(payRollService.updatePayroll(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<String> deletePayroll(@PathVariable Long id) {
        payRollService.deletePayroll(id);
        return ResponseEntity.ok("Payroll record Deleted successfully");
    }
}