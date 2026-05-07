package com.hrm.system.repository;

import com.hrm.system.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByUserId(Long userId);
}