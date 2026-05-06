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
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    //Apply leave
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> applyLeave(@RequestBody LeaveDto dto){
        LeaveDto applied = leaveService.applyLeave(dto);
        return new ResponseEntity<>(applied, HttpStatus.CREATED);
    }

    //get all leave request
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getAllLeave(){
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    //get leave by id
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<LeaveDto> getLeaveById(@PathVariable Long id){
        return ResponseEntity.ok(leaveService.getLeaveById(id));
    }

    //get all leave by userid
    @GetMapping("/user/userId")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public  ResponseEntity<List<LeaveDto>> getLeaveByUserId(Long userId){
        return ResponseEntity.ok(leaveService.getLeaveByUserID(userId));
    }

    // Get leaves filtered by status (SUPER_ADMIN, ADMIN only)
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDto>> getLeaveByStatus(@PathVariable String status) {
        return ResponseEntity.ok(leaveService.getLeaveByStatus(status));
    }

    //Approve leave
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN' or hasRole('ADMIN'))")
    public ResponseEntity<LeaveDto> approveLeave(@PathVariable Long id){
        return  ResponseEntity.ok(leaveService.approveLeave(id));
    }

    //Reject Leave
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERADMIN' or hasRole('ADMIN'))")
    public ResponseEntity<LeaveDto> rejectLeave(@PathVariable Long id){
        return ResponseEntity.ok(leaveService.rejectLeave(id));
    }

    //Update Leave
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN' or hasRole('ADMIN'))")
    public ResponseEntity<LeaveDto> updateLeave(@PathVariable Long id , @RequestBody LeaveDto dto){
        return ResponseEntity.ok(leaveService.updateLeave(id, dto));
    }

    //Delete leave
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<String> deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.ok("Leave request deleted successfully.");
    }
}
