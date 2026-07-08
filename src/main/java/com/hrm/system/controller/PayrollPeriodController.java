package com.hrm.system.controller;

import com.hrm.system.dto.PayrollPeriodDto;
import com.hrm.system.service.PayrollPeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/periods")
@CrossOrigin(origins = "*")
public class PayrollPeriodController {

    private final PayrollPeriodService payrollPeriodService;

    @Autowired
    public PayrollPeriodController(PayrollPeriodService payrollPeriodService) {
        this.payrollPeriodService = payrollPeriodService;
    }

    @PostMapping
    public ResponseEntity<PayrollPeriodDto> createPayrollPeriod(@RequestBody PayrollPeriodDto dto) {
        PayrollPeriodDto created = payrollPeriodService.createPayrollPeriod(
                dto.getMonth(), dto.getYear(), dto.getCompany(), dto.getDepartment());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<PayrollPeriodDto> lockPayrollPeriod(@PathVariable Long id, @RequestParam Long userId) {
        PayrollPeriodDto locked = payrollPeriodService.lockPayrollPeriod(id, userId);
        return ResponseEntity.ok(locked);
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<PayrollPeriodDto> unlockPayrollPeriod(@PathVariable Long id, @RequestParam Long userId) {
        PayrollPeriodDto unlocked = payrollPeriodService.unlockPayrollPeriod(id, userId);
        return ResponseEntity.ok(unlocked);
    }

    @GetMapping
    public ResponseEntity<List<PayrollPeriodDto>> getAllPayrollPeriods() {
        List<PayrollPeriodDto> periods = payrollPeriodService.getAllPayrollPeriods();
        return ResponseEntity.ok(periods);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayrollPeriodDto> getPayrollPeriodById(@PathVariable Long id) {
        PayrollPeriodDto period = payrollPeriodService.getPayrollPeriodById(id);
        return ResponseEntity.ok(period);
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isPeriodLocked(@RequestParam String month, @RequestParam Integer year, 
                                                  @RequestParam(required = false) String department) {
        boolean locked = payrollPeriodService.isPeriodLocked(month, year, department);
        return ResponseEntity.ok(locked);
    }
}
