package com.hrm.system.controller;

import com.hrm.system.dto.LeavePolicyDto;
import com.hrm.system.service.LeavePolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave/policy")
public class LeavePolicyController {

    @Autowired
    private LeavePolicyService leavePolicyService;

    /**
     * GET /api/leave/policy
     * Returns all configured leave policies.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeavePolicyDto>> getAllPolicies() {
        return ResponseEntity.ok(leavePolicyService.getAllPolicies());
    }

    /**
     * GET /api/leave/policy/{leaveType}
     * Returns policy for a specific leave type (e.g. SICK, ANNUAL, EIDULFITAR).
     */
    @GetMapping("/{leaveType}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeavePolicyDto> getPolicyByType(@PathVariable String leaveType) {
        return ResponseEntity.ok(leavePolicyService.getPolicyByType(leaveType));
    }

    /**
     * PUT /api/leave/policy/{id}
     * Update an existing policy (admin only).
     * This is how you change days, carry-forward rules, etc. without touching code.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<LeavePolicyDto> updatePolicy(
            @PathVariable Long id,
            @RequestBody LeavePolicyDto dto) {
        return ResponseEntity.ok(leavePolicyService.updatePolicy(id, dto));
    }
}