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

    //create payroll
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> createPayroll(@RequestBody PayRollDto dto){
        PayRollDto created = payRollService.createPayroll(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    //get all payrolls
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<PayRollDto>> getAllPayrolls() {
        return ResponseEntity.ok(payRollService.getAllPayroll());
    }

    //get payroll by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<PayRollDto> getPayrollById(@PathVariable Long id) {
        return ResponseEntity.ok(payRollService.getPayrollById(id));
    }
    //get Payroll by UserID
    @GetMapping("/user/userId")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<PayRollDto>> getPayrollByUserId(@PathVariable Long userId){
        return ResponseEntity.ok(payRollService.getPayrollByUserId(userId));
    }

    //update payroll record
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> updatePayroll(@PathVariable Long id, @RequestBody PayRollDto dto){
        return ResponseEntity.ok(payRollService.updatePayroll(id, dto));
    }

    //Delete Payroll
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<String> deletePayroll(@PathVariable Long id){
     payRollService.deletePayroll(id);
     return ResponseEntity.ok("Payroll record Deleted successfully");
    }
}
