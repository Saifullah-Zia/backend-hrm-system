package com.hrm.system.controller;

import com.hrm.system.dto.ProbationDto;
import com.hrm.system.service.ProbationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/probation")
@RequiredArgsConstructor
public class ProbationController {

    private final ProbationService probationService;

    // ─────────────────────────────────────────────────────
    // GET — all employees currently ON_PROBATION
    // ─────────────────────────────────────────────────────
    @GetMapping("/on-probation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ProbationDto.Response>> getOnProbation() {
        return ResponseEntity.ok(probationService.getOnProbation());
    }

    // ─────────────────────────────────────────────────────
    // GET — employees whose probation COMPLETED, awaiting HR confirmation
    // ─────────────────────────────────────────────────────
    @GetMapping("/awaiting-confirmation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ProbationDto.Response>> getAwaitingConfirmation() {
        return ResponseEntity.ok(probationService.getAwaitingConfirmation());
    }

    // ─────────────────────────────────────────────────────
    // GET — all CONFIRMED permanent staff
    // ─────────────────────────────────────────────────────
    @GetMapping("/confirmed")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ProbationDto.Response>> getConfirmed() {
        return ResponseEntity.ok(probationService.getConfirmed());
    }

    // ─────────────────────────────────────────────────────
    // POST — HR confirms an employee as permanent staff
    // ─────────────────────────────────────────────────────
    @PostMapping("/confirm/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ProbationDto.Response> confirmProbation(
            @PathVariable Long userId,
            @RequestBody ProbationDto.ConfirmRequest request) {
        return ResponseEntity.ok(
                probationService.confirmProbation(userId, request.getConfirmedByAdminId()));
    }

    // ─────────────────────────────────────────────────────
    // GET — check if a specific user is on probation
    // ─────────────────────────────────────────────────────
    @GetMapping("/check/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Boolean> isOnProbation(@PathVariable Long userId) {
        return ResponseEntity.ok(probationService.isOnProbation(userId));
    }
}