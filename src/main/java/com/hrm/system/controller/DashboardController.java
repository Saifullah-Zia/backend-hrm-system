package com.hrm.system.controller;

import com.hrm.system.model.CorrectionStatus;
import com.hrm.system.model.LeaveStatus;
import com.hrm.system.repository.AttendanceCorrectionRequestRepository;
import com.hrm.system.repository.LeaveRepository;
import com.hrm.system.service.ProbationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final LeaveRepository leaveRepository;
    private final AttendanceCorrectionRequestRepository correctionRepository;
    private final ProbationService probationService;

    /** Numbers for the HR sidebar badges, so the sidebar never downloads the full lists. */
    @GetMapping("/pending-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Long>> pendingCounts() {
        return ResponseEntity.ok(Map.of(
                "leaves", leaveRepository.countByStatus(LeaveStatus.PENDING),
                "corrections", correctionRepository.countByStatus(CorrectionStatus.PENDING),
                "probation", (long) probationService.getAwaitingConfirmation().size()));
    }
}
