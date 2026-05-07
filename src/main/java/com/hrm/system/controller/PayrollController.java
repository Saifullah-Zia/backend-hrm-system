package com.hrm.system.controller;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.repository.PayrollRepository;
import com.hrm.system.service.PayRollService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> createPayroll(@RequestBody PayRollDto dto) {
        PayRollDto created = payRollService.createPayroll(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")  //
    public ResponseEntity<List<PayRollDto>> getAllPayrolls() {
        return ResponseEntity.ok(payRollService.getAllPayroll());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<PayRollDto>> getPayrollByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(payRollService.getPayrollByUserId(userId));
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