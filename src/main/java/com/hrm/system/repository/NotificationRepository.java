package com.hrm.system.repository;

import com.hrm.system.model.Notification;
import com.hrm.system.model.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    Long countByUserIdAndStatus(Long userId, String status);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ' WHERE n.userId = :userId AND n.id = :notificationId")
    void markAsRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ' WHERE n.userId = :userId")
    void markAllAsRead(@Param("userId") Long userId);

    // ── Per-type unread tracking (sidebar badges) ────────────────────────────

    /** Number of distinct notices the user has not opened yet. */
    @Query("SELECT COUNT(DISTINCT n.referenceId) FROM Notification n " +
            "WHERE n.userId = :userId AND n.type = :type AND n.status = 'UNREAD'")
    Long countDistinctUnreadReferencesByType(@Param("userId") Long userId,
                                             @Param("type") String type);

    /** Distinct payslips the employee can see that still have an unread PAYROLL notification. */
    @Query("SELECT COUNT(DISTINCT n.referenceId) FROM Notification n " +
            "WHERE n.userId = :userId AND n.type = 'PAYROLL' AND n.status = 'UNREAD' " +
            "AND EXISTS (SELECT 1 FROM Payroll p WHERE p.id = n.referenceId AND p.status IN :visibleStatuses)")
    Long countUnreadPayslips(@Param("userId") Long userId,
                             @Param("visibleStatuses") Collection<PayrollStatus> visibleStatuses);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ' " +
            "WHERE n.userId = :userId AND n.type = :type AND n.status = 'UNREAD'")
    int markAllAsReadByType(@Param("userId") Long userId, @Param("type") String type);
}