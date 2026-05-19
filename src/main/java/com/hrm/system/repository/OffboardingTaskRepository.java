package com.hrm.system.repository;

import com.hrm.system.enumm.OffboardingTaskCategory;
import com.hrm.system.enumm.OffboardingTaskStatus;
import com.hrm.system.model.OffboardingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OffboardingTaskRepository extends JpaRepository<OffboardingTask, Long> {

    List<OffboardingTask> findByResignationId(Long resignationId);

    List<OffboardingTask> findByResignationIdAndTaskStatus(
            Long resignationId, OffboardingTaskStatus status);

    List<OffboardingTask> findByResignationIdAndCategory(
            Long resignationId, OffboardingTaskCategory category);

    List<OffboardingTask> findByAssignedToIdAndTaskStatus(
            Long userId, OffboardingTaskStatus status);

    long countByResignationId(Long resignationId);
    long countByResignationIdAndTaskStatus(Long resignationId, OffboardingTaskStatus status);

    @Query("SELECT t FROM OffboardingTask t WHERE t.dueDate < :today " +
            "AND t.taskStatus IN ('PENDING', 'IN_PROGRESS')")
    List<OffboardingTask> findOverdueTasks(@Param("today") LocalDate today);

    // ← ADDED for cascade delete
    void deleteByResignationId(Long resignationId);
}