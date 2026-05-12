package com.hrm.system.controller;

import com.hrm.system.dto.LeaveDto;
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

    // Apply leave
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> applyLeave(@RequestBody LeaveDto dto) {
        LeaveDto applied = leaveService.applyLeave(dto);
        return new ResponseEntity<>(applied, HttpStatus.CREATED);
    }

    // Get all leave requests
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getAllLeave() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    // Get leave by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> getLeaveById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.getLeaveById(id));
    }

    // Get all leaves by user ID
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<LeaveDto>> getLeaveByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(leaveService.getLeaveByUserID(userId));
    }

    // Get leaves filtered by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getLeaveByStatus(@PathVariable String status) {
        return ResponseEntity.ok(leaveService.getLeaveByStatus(status));
    }

    // Approve leave
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")  // ✅ fixed syntax
    public ResponseEntity<LeaveDto> approveLeave(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.approveLeave(id));
    }

    // Reject leave
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")  // ✅ fixed syntax
    public ResponseEntity<LeaveDto> rejectLeave(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.rejectLeave(id));
    }

    // Update leave
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")  // ✅ fixed syntax
    public ResponseEntity<LeaveDto> updateLeave(@PathVariable Long id, @RequestBody LeaveDto dto) {
        return ResponseEntity.ok(leaveService.updateLeave(id, dto));
    }

    // Delete leave
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<String> deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.ok("Leave request deleted successfully.");
    }
}