package com.hrm.system.controller;

import com.hrm.system.dto.LeaveBalanceDto;
import com.hrm.system.dto.LeaveDto;
import com.hrm.system.service.LeaveBalanceService;
import com.hrm.system.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    // ── Apply ────────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> applyLeave(@RequestBody LeaveDto dto) {
        return new ResponseEntity<>(leaveService.applyLeave(dto), HttpStatus.CREATED);
    }

    // ── Get all ──────────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getAllLeave() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    // ── Get by ID ────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> getLeaveById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.getLeaveById(id));
    }

    // ── Get by user ──────────────────────────────────────────────────────────
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeaveDto>> getLeaveByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(leaveService.getLeaveByUserID(userId));
    }

    // ── Get by status ────────────────────────────────────────────────────────
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getLeaveByStatus(@PathVariable String status) {
        return ResponseEntity.ok(leaveService.getLeaveByStatus(status));
    }

    // ── Approve ──────────────────────────────────────────────────────────────
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<LeaveDto> approveLeave(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.approveLeave(id));
    }

    // ── Reject ───────────────────────────────────────────────────────────────
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<LeaveDto> rejectLeave(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.rejectLeave(id));
    }

    // ── Update ───────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> updateLeave(@PathVariable Long id, @RequestBody LeaveDto dto) {
        return ResponseEntity.ok(leaveService.updateLeave(id, dto));
    }

    // ── Delete ───────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<String> deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.ok("Leave request withdrawn successfully.");
    }

    // ── Shortcut: my balances (pass userId as query param) ───────────────────
    // Example: GET /api/leave/my-balance?userId=5
    @GetMapping("/my-balance")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeaveBalanceDto>> getMyBalance(@RequestParam Long userId) {
        return ResponseEntity.ok(leaveBalanceService.getBalancesForUser(userId));
    }
}