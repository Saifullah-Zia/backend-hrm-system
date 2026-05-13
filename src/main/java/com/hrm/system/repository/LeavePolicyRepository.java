package com.hrm.system.repository;

import com.hrm.system.model.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {

    Optional<LeavePolicy> findByLeaveType(String leaveType);

     boolean existsByLeaveType(String leaveType);
}