package com.hrm.system.repository;

import com.hrm.system.model.Payroll;
import com.hrm.system.model.PayrollPeriod;
import com.hrm.system.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    // ─── Paginated ────────────────────────────────────────────────────────────
    Page<Payroll> findAll(Pageable pageable);
    Page<Payroll> findByUserId(Long userId, Pageable pageable);

    // ─── Non-paginated (kept for internal use) ────────────────────────────────
    List<Payroll> findByUserId(Long userId);

    // ─── Payroll period integration ───────────────────────────────────────────
    Optional<Payroll> findByUserAndPayrollPeriod(User user, PayrollPeriod payrollPeriod);
    List<Payroll> findByPayrollPeriod(PayrollPeriod payrollPeriod);
}