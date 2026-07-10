package com.hrm.system.repository;

import com.hrm.system.model.Leave;
import com.hrm.system.model.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByStatus(LeaveStatus status);

    List<Leave> findByUserId(Long userId);

    List<Leave> findByUserIdAndType(Long userId, String type);

    @Query("SELECT l FROM Leave l WHERE l.user.id = :userId AND l.type = :type AND " +
            "YEAR(l.startDate) = :year AND l.status = :status")
    List<Leave> findByUserIdAndTypeAndYearAndStatus(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("year") int year,
            @Param("status") LeaveStatus status);

    @Query("SELECT l FROM Leave l WHERE (:status IS NULL OR l.status = :status)")
    Page<Leave> findAllPaged(
            @Param("status") LeaveStatus status,
            Pageable pageable);

    @Query("SELECT l FROM Leave l WHERE l.status = :status " +
            "AND l.startDate <= :endDate AND l.endDate >= :startDate " +
            "AND l.user.id IN :userIds")
    List<Leave> findByStatusAndDateRangeOverlapAndUserIdIn(
            @Param("status") LeaveStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userIds") List<Long> userIds);
}