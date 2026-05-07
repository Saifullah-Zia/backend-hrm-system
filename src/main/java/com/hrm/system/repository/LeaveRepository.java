package com.hrm.system.repository;

import com.hrm.system.model.Leave;
import com.hrm.system.model.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByStatus(LeaveStatus status);
    List<Leave> findByUserId(Long userId);
}
