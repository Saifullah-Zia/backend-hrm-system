package com.hrm.system.repository;

import com.hrm.system.model.OfficeHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeHoursRepository extends JpaRepository<OfficeHours, Long> {
    // Always only one row (id = 1), no extra queries needed
}