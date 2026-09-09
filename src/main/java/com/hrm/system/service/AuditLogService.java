package com.hrm.system.service;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.AuditLog;
import com.hrm.system.model.User;
import com.hrm.system.repository.AuditLogRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public-facing audit log API.
 *
 * IMPORTANT: this class must NEVER call an @Async/@Transactional method on
 * itself (i.e. `this.someAsyncMethod()`). Doing so bypasses the Spring AOP
 * proxy entirely, silently ignoring @Async and @Transactional. That was the
 * root cause of the payroll rollback incident. All async/transactional log
 * writes are delegated to AuditLogExecutor, a *separate* bean, so calls go
 * through the proxy correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogExecutor auditLogExecutor;

    // LOG — full request, delegates to the async executor bean
    public void log(AuditLogDto.LogRequest request) {
        auditLogExecutor.log(request);
    }

    // Convenience overload — no IP, no JSON snapshots
    // Delegates to the executor bean directly (NOT this.log(...))
    public void log(String entityName, Long entityId,
                    AuditAction action, String description, Long performedByUserId) {
        auditLogExecutor.log(AuditLogDto.LogRequest.builder()
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .description(description)
                .performedByUserId(performedByUserId)
                .build());
    }

    // GET history for a single record
    // e.g. full edit history of Payroll#42
    @Transactional(readOnly = true)
    public List<AuditLogDto.Response> getEntityHistory(String entityName, Long entityId) {
        return auditLogRepository
                .findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // GET all activity by a user (paged)
    @Transactional(readOnly = true)
    public Page<AuditLogDto.Response> getUserActivity(Long userId, Pageable pageable) {
        return auditLogRepository
                .findByPerformedBy_IdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    // GET filtered paged logs for audit dashboard
    @Transactional(readOnly = true)
    public Page<AuditLogDto.Response> getFiltered(
            AuditLogDto.FilterRequest filter, Pageable pageable) {
        return auditLogRepository.findAllFiltered(
                        filter.getEntityName(),
                        filter.getAction(),
                        filter.getFrom(),
                        filter.getTo(),
                        pageable)
                .map(this::mapToResponse);
    }

    // MAPPER — Entity → Response DTO
    private AuditLogDto.Response mapToResponse(AuditLog a) {
        User performer = a.getPerformedBy();
        String performerName = a.getPerformedByName();
        if ((performerName == null || performerName.isBlank()) && performer != null) {
            performerName = performer.getName();
        }
        if (performerName == null || performerName.isBlank()) {
            performerName = "Deleted user";
        }

        return AuditLogDto.Response.builder()
                .id(a.getId())
                .entityName(a.getEntityName())
                .entityId(a.getEntityId())
                .action(a.getAction())
                .description(a.getDescription())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .performedById(performer != null ? performer.getId() : null)
                .performedByName(performerName)
                .ipAddress(a.getIpAddress())
                .createdAt(a.getCreatedAt())
                .build();
    }
}