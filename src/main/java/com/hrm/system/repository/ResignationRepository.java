package com.hrm.system.repository;

import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.model.Resignation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Repository
public interface ResignationRepository extends JpaRepository<Resignation, Long> {

    List<Resignation> findByEmployeeProfile_Id(Long employeeProfileId);

    Optional<Resignation> findByEmployeeProfile_IdAndStatusNot(Long employeeProfileId, ResignationStatus status);

    List<Resignation> findByStatus(ResignationStatus status);

    @Query("SELECT r FROM Resignation r WHERE r.lastWorkingDay <= :today AND r.status = 'APPROVED'")
    List<Resignation> findResignationsDueForCompletion(@Param("today") LocalDate today);

    boolean existsByEmployeeProfile_IdAndStatusIn(Long employeeProfileId, List<ResignationStatus> statuses);

    void deleteByEmployeeProfile_Id(Long employeeProfileId);

    // Add this method
    @Query("SELECT r FROM Resignation r WHERE (:status IS NULL OR r.status = :status)")
    Page<Resignation> findAllPaged(
            @Param("status") ResignationStatus status,
            Pageable pageable);
}