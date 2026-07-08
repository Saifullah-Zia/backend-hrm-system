package com.hrm.system.repository;

import com.hrm.system.model.AllowanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllowanceTypeRepository extends JpaRepository<AllowanceType, Long> {
    List<AllowanceType> findByIsActiveTrue();
}
