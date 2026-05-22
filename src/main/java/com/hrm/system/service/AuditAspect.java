package com.hrm.system.service;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    // ✅ Targets all Controllers inside com.hrm.system.controller
    @AfterReturning(
            pointcut = "(execution(* com.hrm.system.controller..*Controller*.create*(..)) " +
                    "|| execution(* com.hrm.system.controller..*Controller*.save*(..)) " +
                    "|| execution(* com.hrm.system.controller..*Controller*.add*(..))" +
                    "|| execution(* com.hrm.system.controller..*Controller*.publish*(..))" +
                    "|| execution(* com.hrm.system.controller..*Controller*.post*(..)))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logCreate(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Create: {}", jp.getSignature().toShortString());

            Long userId = getCurrentUserId();
            if (userId == null) {
                log.warn("Audit skipped - userId is null");
                return;
            }

            // ✅ Safe Entity Name extraction (Handles ResponseEntity and direct entities)
            String entityName = "Unknown";
            if (result != null) {
                if (result instanceof ResponseEntity) {
                    Object body = ((ResponseEntity<?>) result).getBody();
                    if (body != null) {
                        entityName = body.getClass().getSimpleName();
                    }
                } else {
                    entityName = result.getClass().getSimpleName();
                }
            }

            // Fallback: Use Controller name (e.g., DepartmentController -> Department)
            if ("Unknown".equals(entityName) || "ResponseEntity".equals(entityName)) {
                String className = jp.getSignature().getDeclaringType().getSimpleName();
                entityName = className.replace("Controller", "").replace("ApiController", "");
            }

            // Clean up entity name to avoid suffixes like Dto, DTO or Entity
            entityName = entityName.replace("Dto", "").replace("DTO", "").replace("Entity", "");

            Long entityId = extractEntityId(result, jp);

            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.CREATE)
                    .description("Created " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logCreate failed: ", e);
        }
    }

    // ✅ Targets all Controllers inside com.hrm.system.controller
    @AfterReturning(
            pointcut = "(execution(* com.hrm.system.controller..*Controller*.update*(..)) " +
                    "|| execution(* com.hrm.system.controller..*Controller*.edit*(..))" +
                    "|| execution(* com.hrm.system.controller..*Controller*.put*(..)))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logUpdate(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Update: {}", jp.getSignature().toShortString());

            Long userId = getCurrentUserId();
            if (userId == null) return;

            String entityName = "Unknown";
            if (result != null) {
                if (result instanceof ResponseEntity) {
                    Object body = ((ResponseEntity<?>) result).getBody();
                    if (body != null) {
                        entityName = body.getClass().getSimpleName();
                    }
                } else {
                    entityName = result.getClass().getSimpleName();
                }
            }

            if ("Unknown".equals(entityName) || "ResponseEntity".equals(entityName)) {
                String className = jp.getSignature().getDeclaringType().getSimpleName();
                entityName = className.replace("Controller", "").replace("ApiController", "");
            }

            // Clean up entity name to avoid suffixes like Dto, DTO or Entity
            entityName = entityName.replace("Dto", "").replace("DTO", "").replace("Entity", "");

            Long entityId = extractEntityId(result, jp);

            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.UPDATE)
                    .description("Updated " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logUpdate failed: ", e);
        }
    }

    // ✅ Targets all Controllers inside com.hrm.system.controller
    @After(
            value = "(execution(* com.hrm.system.controller..*Controller*.delete*(..)))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))"
    )
    public void logDelete(JoinPoint jp) {
        try {
            log.info("Audit Aspect - Intercepted Controller Delete: {}", jp.getSignature().toShortString());

            Long userId = getCurrentUserId();
            if (userId == null) return;

            String className = jp.getSignature().getDeclaringType().getSimpleName();
            String entityName = className.replace("Controller", "").replace("ApiController", "");
            
            // Clean up entity name to avoid suffixes like Dto, DTO or Entity
            entityName = entityName.replace("Dto", "").replace("DTO", "").replace("Entity", "");

            Long entityId = extractEntityId(null, jp);

            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.DELETE)
                    .description("Deleted " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logDelete failed: ", e);
        }
    }

    // ✅ SUBMIT — submit* (e.g. submitResignation, applyLeave)
    @AfterReturning(
            pointcut = "(execution(* com.hrm.system.controller..*Controller*.submit*(..)) " +
                    "|| execution(* com.hrm.system.controller..*Controller*.apply*(..)))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logSubmit(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Submit: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.SUBMIT)
                    .description("Submitted " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logSubmit failed: ", e);
        }
    }

    // ✅ APPROVE — approve*, confirm* (e.g. approveLeave, confirmProbation)
    @AfterReturning(
            pointcut = "(execution(* com.hrm.system.controller..*Controller*.approve*(..)) " +
                    "|| execution(* com.hrm.system.controller..*Controller*.confirm*(..)))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logApprove(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Approve: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.APPROVE)
                    .description("Approved " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logApprove failed: ", e);
        }
    }

    // ✅ REJECT — reject* (e.g. rejectLeave)
    @AfterReturning(
            pointcut = "execution(* com.hrm.system.controller..*Controller*.reject*(..))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logReject(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Reject: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.REJECT)
                    .description("Rejected " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logReject failed: ", e);
        }
    }

    // ✅ APPROVE or REJECT — process* (e.g. processResignation — detects outcome from response status field)
    @AfterReturning(
            pointcut = "execution(* com.hrm.system.controller..*Controller*.process*(..))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logProcess(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Process: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);

            // Detect APPROVE vs REJECT by reading the status field from the response body
            AuditAction action = AuditAction.UPDATE;
            String description = "Processed " + entityName;
            Object body = result;
            if (result instanceof ResponseEntity) {
                body = ((ResponseEntity<?>) result).getBody();
            }
            if (body != null) {
                try {
                    java.lang.reflect.Method getStatus = body.getClass().getMethod("getStatus");
                    Object statusVal = getStatus.invoke(body);
                    if (statusVal != null) {
                        String statusStr = statusVal.toString().toUpperCase();
                        if (statusStr.contains("APPROVED") || statusStr.contains("APPROVE")) {
                            action = AuditAction.APPROVE;
                            description = "Approved " + entityName;
                        } else if (statusStr.contains("REJECTED") || statusStr.contains("REJECT")) {
                            action = AuditAction.REJECT;
                            description = "Rejected " + entityName;
                        }
                    }
                } catch (Exception ignored) {}
            }

            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(action)
                    .description(description)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logProcess failed: ", e);
        }
    }

    // ✅ WITHDRAW — withdraw* (e.g. withdrawResignation)
    @AfterReturning(
            pointcut = "execution(* com.hrm.system.controller..*Controller*.withdraw*(..))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logWithdraw(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Withdraw: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.WITHDRAW)
                    .description("Withdrew " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logWithdraw failed: ", e);
        }
    }

    // ✅ COMPLETE — complete* (e.g. completeOffboarding)
    @AfterReturning(
            pointcut = "execution(* com.hrm.system.controller..*Controller*.complete*(..))" +
                    " && !execution(* com.hrm.system.controller.AuditLogController.*(..))",
            returning = "result"
    )
    public void logComplete(JoinPoint jp, Object result) {
        try {
            log.info("Audit Aspect - Intercepted Controller Complete: {}", jp.getSignature().toShortString());
            Long userId = getCurrentUserId();
            if (userId == null) return;
            String entityName = resolveEntityName(result, jp);
            Long entityId = extractEntityId(result, jp);
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(AuditAction.COMPLETE)
                    .description("Completed " + entityName)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("Audit logComplete failed: ", e);
        }
    }

    // ─── Shared helper: resolve clean entity name from result or controller class ───
    private String resolveEntityName(Object result, JoinPoint jp) {
        String entityName = "Unknown";
        if (result != null) {
            Object body = result instanceof ResponseEntity
                    ? ((ResponseEntity<?>) result).getBody()
                    : result;
            if (body != null) {
                entityName = body.getClass().getSimpleName();
            }
        }
        if ("Unknown".equals(entityName) || "ResponseEntity".equals(entityName)) {
            String className = jp.getSignature().getDeclaringType().getSimpleName();
            entityName = className.replace("Controller", "").replace("ApiController", "");
        }
        return entityName
                .replace("Dto", "").replace("DTO", "")
                .replace("Entity", "").replace("Response", "");
    }

    private Long extractEntityId(Object result, JoinPoint jp) {
        if (result != null) {
            Object body = result;
            if (result instanceof ResponseEntity) {
                body = ((ResponseEntity<?>) result).getBody();
            }
            if (body != null) {
                // 1. Try calling getId()
                try {
                    java.lang.reflect.Method getIdMethod = body.getClass().getMethod("getId");
                    Object idVal = getIdMethod.invoke(body);
                    if (idVal != null) {
                        return Long.parseLong(idVal.toString());
                    }
                } catch (Exception ignored) {}

                // 2. Try accessing "id" field directly
                try {
                    java.lang.reflect.Field idField = body.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    Object idVal = idField.get(body);
                    if (idVal != null) {
                        return Long.parseLong(idVal.toString());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Try extracting from method arguments (Path variables / Request params)
        if (jp != null && jp.getArgs() != null) {
            for (Object arg : jp.getArgs()) {
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof Integer) {
                    return ((Integer) arg).longValue();
                }
            }
        }

        // Fallback for database NOT NULL constraint
        return 0L;
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attr =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            Object userId = attr.getRequest().getAttribute("userId");
            if (userId != null) {
                return Long.parseLong(userId.toString());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp() {
        try {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                            .getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            return ip != null ? ip.split(",")[0] : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
