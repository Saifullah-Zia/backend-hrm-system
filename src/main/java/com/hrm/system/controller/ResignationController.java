package com.hrm.system.controller;

import com.hrm.system.dto.ResignationDto;
import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.service.ResignationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;



        import java.util.List;

@RestController
@RequestMapping("/api/resignations")
@RequiredArgsConstructor
public class ResignationController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.hrm.system.security.AccessGuard accessGuard;


    private final ResignationService resignationService;

    // ─────────────────────────────────────────────────────
    // POST /api/resignations
    // Employee submits resignation
    // ─────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> submit(
            @RequestBody ResignationDto.Request request) {
        accessGuard.requireOwnProfileOrPrivileged(request.getEmployeeId());
        return ResponseEntity.ok(resignationService.submitResignation(request));
    }

    // GET /api/resignations/paged?page=0&size=10&status=PENDING
    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Page<ResignationDto.Response>> getPaged(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    ResignationStatus status) {

        Pageable pageable = com.hrm.system.util.PageLimits.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(resignationService.getPaged(status, pageable));
    }

    // ─────────────────────────────────────────────────────
    // GET /api/resignations
    // HR views all resignations
    // ─────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ResignationDto.Response>> getAll() {
        return ResponseEntity.ok(resignationService.getAllResignations());
    }


    // GET /api/resignations/status/{status}
    // Filter by status

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ResignationDto.Response>> getByStatus(
            @PathVariable ResignationStatus status) {
        return ResponseEntity.ok(resignationService.getByStatus(status));
    }


    // GET /api/resignations/employee/{employeeId}
    // Get all resignations for one employee

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<ResignationDto.Response>> getByEmployee(
            @PathVariable Long employeeId) {
        accessGuard.requireOwnProfileOrPrivileged(employeeId);
        return ResponseEntity.ok(resignationService.getByEmployee(employeeId));
    }


    // GET /api/resignations/{id}
    // Get single resignation

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> getById(@PathVariable Long id) {
        ResignationDto.Response resignation = resignationService.getById(id);
        accessGuard.requireOwnProfileOrPrivileged(resignation.getEmployeeId());
        return ResponseEntity.ok(resignation);
    }


    // PUT /api/resignations/{id}/process
    // HR approves or rejects

    @PutMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ResignationDto.Response> process(
            @PathVariable Long id,
            @RequestBody ResignationDto.ApprovalRequest request,
            @RequestParam(value = "approvedBy", required = false) Long ignoredApprovedBy) {
        return ResponseEntity.ok(resignationService.processResignation(id, request, accessGuard.currentUserId()));
    }


    // PUT /api/resignations/{id}/withdraw
    // Employee withdraws resignation

    @PutMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> withdraw(
            @PathVariable Long id,
            @RequestParam String reason) {
        accessGuard.requireOwnProfileOrPrivileged(resignationService.getById(id).getEmployeeId());
        return ResponseEntity.ok(resignationService.withdrawResignation(id, reason));
    }


    // PUT /api/resignations/{id}/complete
    // Mark offboarding as fully complete

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ResignationDto.Response> complete(@PathVariable Long id) {
        return ResponseEntity.ok(resignationService.completeOffboarding(id));
    }
}