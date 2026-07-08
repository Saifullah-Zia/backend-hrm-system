package com.hrm.system.repository;

import com.hrm.system.model.PayrollPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollPolicyRepository extends JpaRepository<PayrollPolicy, Long> {
    Optional<PayrollPolicy> findByIsActiveTrue();
}
