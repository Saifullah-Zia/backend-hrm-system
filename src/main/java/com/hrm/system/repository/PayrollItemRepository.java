package com.hrm.system.repository;

import com.hrm.system.model.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {
    List<PayrollItem> findByPayrollId(Long payrollId);
}
