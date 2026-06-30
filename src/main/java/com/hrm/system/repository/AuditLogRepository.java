package com.hrm.system.repository;

import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // All changes to a specific record (e.g. all Payroll#42 edits)
    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDesc(
            String entityName, Long entityId);

    // All activity by a specific user
    Page<AuditLog> findByPerformedBy_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Filtered paged view for HR audit dashboard
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityName IS NULL OR a.entityName = :entityName)
              AND (cast(:action as text) IS NULL OR a.action = :action)
              AND (cast(:from as timestamp) IS NULL OR a.createdAt >= :from)
              AND (cast(:to as timestamp) IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findAllFiltered(
            @Param("entityName") String entityName,
            @Param("action")     AuditAction action,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            value = """
                    UPDATE audit_logs al
                    SET performed_by_name = COALESCE(NULLIF(al.performed_by_name, ''), u.name),
                        performed_by = NULL
                    FROM users u
                    WHERE al.performed_by = u.id AND u.id = :userId
                    """,
            nativeQuery = true)
    void nullifyPerformedBy(@Param("userId") Long userId);
}