package com.hrm.system.controller;

import com.hrm.system.dto.OffboardingTaskDto;
import com.hrm.system.service.OffboardingTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/offboarding-tasks")
@RequiredArgsConstructor
public class OffboardingTaskController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.system.service.ResignationService resignationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.system.security.AccessGuard accessGuard;


    private final OffboardingTaskService offboardingTaskService;


    // POST /api/offboarding-tasks
    // Add a custom task to an offboarding checklist

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<OffboardingTaskDto.Response> create(
            @RequestBody OffboardingTaskDto.Request request) {
        return ResponseEntity.ok(offboardingTaskService.createTask(request));
    }


    // GET /api/offboarding-tasks/resignation/{resignationId}
    // Get full checklist for a resignation

    @GetMapping("/resignation/{resignationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<OffboardingTaskDto.Response>> getByResignation(
            @PathVariable Long resignationId) {
        accessGuard.requireOwnProfileOrPrivileged(resignationService.getById(resignationId).getEmployeeId());
        return ResponseEntity.ok(offboardingTaskService.getTasksByResignation(resignationId));
    }


    // GET /api/offboarding-tasks/my-tasks/{userId}
    // Get pending tasks assigned to a user

    @GetMapping("/my-tasks/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<OffboardingTaskDto.Response>> getMyTasks(
            @PathVariable Long userId) {
        accessGuard.requireSelfOrPrivileged(userId);
        return ResponseEntity.ok(offboardingTaskService.getMyPendingTasks(userId));
    }


    // GET /api/offboarding-tasks/overdue
    // Get all overdue tasks

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<OffboardingTaskDto.Response>> getOverdue() {
        return ResponseEntity.ok(offboardingTaskService.getOverdueTasks());
    }


    // PUT /api/offboarding-tasks/{id}
    // Update task status / mark complete

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<OffboardingTaskDto.Response> update(
            @PathVariable Long id,
            @RequestBody OffboardingTaskDto.UpdateRequest request) {
        if (!accessGuard.isPrivileged() && !offboardingTaskService.isTaskParticipant(id, accessGuard.currentUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("You can only update your own tasks");
        }
        return ResponseEntity.ok(offboardingTaskService.updateTask(id, request));
    }
}