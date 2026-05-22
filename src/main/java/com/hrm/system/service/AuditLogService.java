package com.hrm.system.service;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.exception.ResourceNotFoundException;
import com.hrm.system.model.AuditLog;
import com.hrm.system.model.User;
import com.hrm.system.repository.AuditLogRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository     userRepository;

    // LOG — called by other services after any state change
    // Runs asynchronously so it never blocks the main flow
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLogDto.LogRequest request) {
        try {
            User performer = userRepository.findById(request.getPerformedByUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found: " + request.getPerformedByUserId()));

            AuditLog entry = AuditLog.builder()
                    .entityName(request.getEntityName())
                    .entityId(request.getEntityId())
                    .action(request.getAction())
                    .description(request.getDescription())
                    .oldValue(request.getOldValue())
                    .newValue(request.getNewValue())
                    .performedBy(performer)
                    .ipAddress(request.getIpAddress())
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit failures must never crash business logic
            log.error("Failed to write audit log: entity={} id={} action={}",
                    request.getEntityName(), request.getEntityId(),
                    request.getAction(), ex);
        }
    }

    // Convenience overload — no IP, no JSON snapshots
    public void log(String entityName, Long entityId,
                    AuditAction action, String description, Long performedByUserId) {
        log(AuditLogDto.LogRequest.builder()
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
        return AuditLogDto.Response.builder()
                .id(a.getId())
                .entityName(a.getEntityName())
                .entityId(a.getEntityId())
                .action(a.getAction())
                .description(a.getDescription())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .performedById(a.getPerformedBy().getId())
                .performedByName(a.getPerformedBy().getName())
                .ipAddress(a.getIpAddress())
                .createdAt(a.getCreatedAt())
                .build();
    }
}