package com.hrm.system.model;

import com.hrm.system.enumm.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_entity",    columnList = "entity_name, entity_id"),
        @Index(name = "idx_audit_performed", columnList = "performed_by"),
        @Index(name = "idx_audit_created",   columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which domain object was touched (e.g. "Payroll", "EmployeeProfile")
    @Column(nullable = false)
    private String entityName;

    // Primary-key value of that object
    @Column(nullable = false)
    private Long entityId;

    // CREATE | UPDATE | DELETE | APPROVE | REJECT | WITHDRAW …
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    // Human-readable summary: "Salary changed from 50000 to 60000"
    @Column(nullable = false, length = 1000)
    private String description;

    // JSON snapshot of old state (nullable on CREATE)
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    // JSON snapshot of new state (nullable on DELETE)
    @Column(columnDefinition = "TEXT")
    private String newValue;

    // User who triggered the change (nullable so deleting a user doesn't delete audit history)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = true)
    private User performedBy;

    // IP address of the request (optional but useful for compliance)
    private String ipAddress;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;
}