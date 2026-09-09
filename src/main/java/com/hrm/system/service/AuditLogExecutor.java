package com.hrm.system.service;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.model.AuditLog;
import com.hrm.system.model.User;
import com.hrm.system.repository.AuditLogRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes the actual audit-log write asynchronously, in its own isolated
 * transaction.
 *
 * This MUST be a separate Spring bean from AuditLogService. If this method
 * lived on AuditLogService itself and AuditLogService called it via
 * `this.log(request)`, the call would bypass the Spring proxy and both
 * @Async and @Transactional would be silently ignored — the exact bug that
 * caused the bulk payroll rollback incident. Because this is a distinct
 * injected bean, calls into it always go through the proxy correctly.
 *
 * Never call any method on this class via `this.` from within this class if
 * you add more @Async/@Transactional methods here later — the same rule
 * applies recursively.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogExecutor {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLogDto.LogRequest request) {
        try {
            User performer = null;
            if (request.getPerformedByUserId() != null) {
                performer = userRepository.findById(request.getPerformedByUserId()).orElse(null);
            }
            String performerName = performer != null ? performer.getName() : "System";

            AuditLog entry = AuditLog.builder()
                    .entityName(request.getEntityName())
                    .entityId(request.getEntityId())
                    .action(request.getAction())
                    .description(request.getDescription())
                    .oldValue(request.getOldValue())
                    .newValue(request.getNewValue())
                    .performedBy(performer)
                    .performedByName(performerName)
                    .ipAddress(request.getIpAddress())
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit failures must never crash business logic, and must
            // never propagate back into a caller's transaction — this
            // catch block, combined with REQUIRES_NEW + a real proxy call,
            // guarantees that.
            log.error("Failed to write audit log: entity={} id={} action={}",
                    request.getEntityName(), request.getEntityId(),
                    request.getAction(), ex);
        }
    }
}