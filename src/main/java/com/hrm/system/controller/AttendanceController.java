package com.hrm.system.controller;

import com.hrm.system.dto.AttendanceDto;
import com.hrm.system.dto.ManualAttendanceRequestDto;
import com.hrm.system.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody AttendanceDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.save(dto));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Employee checks in — server stamps the current PKT time.
     * Employee never sends a time, so no manipulation possible.
     * POST /api/attendance/checkin?userId=5
     */
    @PostMapping("/checkin")
    public ResponseEntity<?> checkIn(@RequestParam Long userId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(attendanceService.checkIn(userId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Employee checks out — server stamps the current PKT time.
     * POST /api/attendance/checkout?userId=5
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkOut(@RequestParam Long userId) {
        try {
            return ResponseEntity.ok(attendanceService.checkOut(userId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Admin only — full edit of any record.
     * PUT /api/attendance/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole(ADMIN)")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody AttendanceDto dto,
                                    Authentication auth) {
        try {
            return ResponseEntity.ok(attendanceService.adminUpdate(id, dto, auth));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<AttendanceDto>> getAllAttendance() {
        return ResponseEntity.ok(attendanceService.getAll());
    }

    @GetMapping("/paged")
    public ResponseEntity<AttendanceDto.PageResponse> getAllPaginated(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(attendanceService.getAllPaginated(page, size, sortBy, sortDir));
    }

    @GetMapping("/user/{userId}/paged")
    public ResponseEntity<?> getUserPaginated(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            return ResponseEntity.ok(attendanceService.getUserPaginated(userId, page, size, sortBy, sortDir));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAttendanceById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(attendanceService.getById(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole(ADMIN)")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            attendanceService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Admin only — manually trigger absence marking for yesterday's shift date.
     * Useful for testing without waiting for the 2:30 AM scheduled job.
     * POST /api/attendance/mark-absent-yesterday
     */
    @PostMapping("/mark-absent-yesterday")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<String> triggerMarkAbsent() {
        attendanceService.markAllAbsentForYesterday();
        return ResponseEntity.ok("Absent marking completed for yesterday's shift date");
    }

    @PostMapping("/mark-manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> markManualAttendance(@RequestBody ManualAttendanceRequestDto request) {
        try {
            return ResponseEntity.ok(attendanceService.markAttendanceForDateRange(
                    request.getStartDate(), request.getEndDate(), request.getUserIds()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}