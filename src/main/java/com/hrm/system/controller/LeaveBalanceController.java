package com.hrm.system.controller;

import com.hrm.system.dto.LeaveBalanceDto;
import com.hrm.system.service.LeaveBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave/balance")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LeaveBalanceController {

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    /**
     * GET /api/leave/balance/user/{userId}
     * Returns all leave type balances for a specific user in the current year.
     * Employee can see their own; admin can see anyone's.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeaveBalanceDto>> getBalancesForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(leaveBalanceService.getBalancesForUser(userId));
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
     * GET /api/leave/balance/all
     * Admin only — returns all users' balances for the current year.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveBalanceDto>> getAllBalances() {
        return ResponseEntity.ok(leaveBalanceService.getAllBalancesCurrentYear());
    }
}