package com.hrm.system.repository;

import com.hrm.system.model.AttendanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {
    Optional<AttendanceSummary> findByEmployeeIdAndPayrollPeriodId(Long employeeId, Long payrollPeriodId);

    @Query("SELECT s FROM AttendanceSummary s JOIN FETCH s.employee WHERE s.payrollPeriod.id = :payrollPeriodId")
    List<AttendanceSummary> findByPayrollPeriodIdWithEmployee(@Param("payrollPeriodId") Long payrollPeriodId);
}
