package com.hrm.system.dto;

import com.hrm.system.enumm.AuditAction;
import lombok.*;

import java.time.LocalDateTime;

public class AuditLogDto {

    // ── Internal use: other services call log() with this ──────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogRequest {
        private String      entityName;
        private Long        entityId;
        private AuditAction action;
        private String      description;
        private String      oldValue;       // JSON string, may be null
        private String      newValue;       // JSON string, may be null
        private Long        performedByUserId;
        private String      ipAddress;      // may be null
    }

    // ── REST response for audit log entries ─────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long          id;
        private String        entityName;
        private Long          entityId;
        private AuditAction   action;
        private String        description;
        private String        oldValue;
        private String        newValue;
        private Long          performedById;
        private String        performedByName;
        private String        ipAddress;
        private LocalDateTime createdAt;
    }

    // ── Query filter used by the controller ─────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FilterRequest {
        private String        entityName;   // optional
        private AuditAction   action;       // optional
        private LocalDateTime from;         // optional
        private LocalDateTime to;           // optional
    }
}