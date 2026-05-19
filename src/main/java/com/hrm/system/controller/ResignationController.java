package com.hrm.system.controller;

import com.hrm.system.dto.ResignationDto;
import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.service.ResignationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/resignations")
@RequiredArgsConstructor
public class ResignationController {

    private final ResignationService resignationService;

    // ─────────────────────────────────────────────────────
    // POST /api/resignations
    // Employee submits resignation
    // ─────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> submit(
            @RequestBody ResignationDto.Request request) {
        return ResponseEntity.ok(resignationService.submitResignation(request));
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
        return ResponseEntity.ok(resignationService.getByEmployee(employeeId));
    }


    // GET /api/resignations/{id}
    // Get single resignation

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(resignationService.getById(id));
    }


    // PUT /api/resignations/{id}/process
    // HR approves or rejects

    @PutMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ResignationDto.Response> process(
            @PathVariable Long id,
            @RequestBody ResignationDto.ApprovalRequest request,
            @RequestParam Long approvedBy) {
        return ResponseEntity.ok(resignationService.processResignation(id, request, approvedBy));
    }


    // PUT /api/resignations/{id}/withdraw
    // Employee withdraws resignation

    @PutMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<ResignationDto.Response> withdraw(
            @PathVariable Long id,
            @RequestParam String reason) {
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