package com.hrm.system.repository;

import com.hrm.system.model.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    Optional<PayrollPeriod> findByMonthAndYearAndDepartment(String month, Integer year, String department);
    Optional<PayrollPeriod> findByMonthAndYear(String month, Integer year);
    boolean existsByMonthAndYearAndDepartment(String month, Integer year, String department);
}
