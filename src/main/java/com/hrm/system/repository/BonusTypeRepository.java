package com.hrm.system.repository;

import com.hrm.system.model.BonusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonusTypeRepository extends JpaRepository<BonusType, Long> {
    List<BonusType> findByIsActiveTrue();
}
