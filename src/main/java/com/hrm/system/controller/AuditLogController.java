package com.hrm.system.controller;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    // Paged, filtered audit log for HR/Admin dashboard
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto.Response>> getFiltered(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        AuditLogDto.FilterRequest filter = AuditLogDto.FilterRequest.builder()
                .entityName(entityName)
                .action(action)
                .from(from)
                .to(to)
                .build();

        return ResponseEntity.ok(auditLogService.getFiltered(filter, pageable));
    }

    // GET /api/audit-logs/entity/{entityName}/{entityId}
    // Full change history of one record (e.g. Payroll#42)
    @GetMapping("/entity/{entityName}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogDto.Response>> getEntityHistory(
            @PathVariable String entityName,
            @PathVariable Long   entityId) {

        return ResponseEntity.ok(auditLogService.getEntityHistory(entityName, entityId));
    }

    // GET /api/audit-logs/user/{userId}
    // All actions performed by a specific user
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto.Response>> getUserActivity(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(auditLogService.getUserActivity(userId, pageable));
    }
}