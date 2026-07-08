package com.hrm.system.controller;

import com.hrm.system.dto.PayrollPolicyDto;
import com.hrm.system.service.PayrollPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/policies")
@CrossOrigin(origins = "*")
public class PayrollPolicyController {

    private final PayrollPolicyService payrollPolicyService;

    @Autowired
    public PayrollPolicyController(PayrollPolicyService payrollPolicyService) {
        this.payrollPolicyService = payrollPolicyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayrollPolicyDto> createPolicy(@RequestBody PayrollPolicyDto dto) {
        PayrollPolicyDto created = payrollPolicyService.createPolicy(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayrollPolicyDto> updatePolicy(@PathVariable Long id, @RequestBody PayrollPolicyDto dto) {
        PayrollPolicyDto updated = payrollPolicyService.updatePolicy(id, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<PayrollPolicyDto>> getAllPolicies() {
        List<PayrollPolicyDto> policies = payrollPolicyService.getAllPolicies();
        return ResponseEntity.ok(policies);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayrollPolicyDto> getActivePolicy() {
        return payrollPolicyService.getActivePolicy()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayrollPolicyDto> getPolicyById(@PathVariable Long id) {
        PayrollPolicyDto policy = payrollPolicyService.getPolicyById(id);
        return ResponseEntity.ok(policy);
    }
}
