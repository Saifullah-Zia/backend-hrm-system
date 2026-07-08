package com.hrm.system.repository;

import com.hrm.system.model.AttendanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {
    Optional<AttendanceSummary> findByEmployeeIdAndPayrollPeriodId(Long employeeId, Long payrollPeriodId);
}
