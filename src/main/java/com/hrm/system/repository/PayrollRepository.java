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

    @Query("SELECT p FROM Payroll p WHERE " +
           "LOWER(p.user.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CAST(p.status AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CONCAT(p.payrollPeriod.month, ' ', CAST(p.payrollPeriod.year AS string))) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Payroll> searchPayrolls(@Param("search") String search, Pageable pageable);

    Page<Payroll> findByUserId(Long userId, Pageable pageable);

    // ─── Non-paginated (kept for internal use) ────────────────────────────────
    List<Payroll> findByUserId(Long userId);

    // ─── Payroll period integration ───────────────────────────────────────────
    Optional<Payroll> findByUserAndPayrollPeriod(User user, PayrollPeriod payrollPeriod);
    List<Payroll> findByPayrollPeriod(PayrollPeriod payrollPeriod);
}