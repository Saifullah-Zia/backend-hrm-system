package com.hrm.system.controller;

import com.hrm.system.dto.LeaveBalanceDto;
import com.hrm.system.dto.LeaveBalanceUpdateRequest;
import com.hrm.system.service.LeaveBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave/balance")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LeaveBalanceController {

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    /**
     * GET /api/leave/balance/user/{userId}?page=0&size=10
     * Returns paginated leave type balances for a specific user in the current year.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Page<LeaveBalanceDto>> getBalancesForUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(leaveBalanceService.getBalancesForUser(userId, page, size));
    }

    /**
     * GET /api/leave/balance/user/{userId}/type/{leaveType}
     * Returns a single leave type balance for a specific user.
     */
    @GetMapping("/user/{userId}/type/{leaveType}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveBalanceDto> getBalanceByType(
            @PathVariable Long userId,
            @PathVariable String leaveType) {
        return ResponseEntity.ok(leaveBalanceService.getBalance(userId, leaveType));
    }

    /**
     * GET /api/leave/balance/all?page=0&size=10
     * Admin only — returns all users' balances for the current year (paginated).
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Page<LeaveBalanceDto>> getAllBalances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(leaveBalanceService.getAllBalancesCurrentYear(page, size));
    }

    /**
     * PUT /api/leave/balance/{id}
     * Admin / super admin — manually adjust total, used, pending, or carry-forward days.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<LeaveBalanceDto> updateBalance(
            @PathVariable Long id,
            @RequestBody LeaveBalanceUpdateRequest request) {
        return ResponseEntity.ok(leaveBalanceService.updateBalance(id, request));
    }
}