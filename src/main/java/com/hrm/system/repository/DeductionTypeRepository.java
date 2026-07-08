package com.hrm.system.repository;

import com.hrm.system.model.DeductionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeductionTypeRepository extends JpaRepository<DeductionType, Long> {
    List<DeductionType> findByIsActiveTrue();
}
