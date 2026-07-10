package com.hrm.system.repository;

import com.hrm.system.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByIsActiveTrueOrderByDateAsc();
    Optional<Holiday> findByDateAndIsActiveTrue(LocalDate date);
    List<Holiday> findByDateBetweenAndIsActiveTrue(LocalDate startDate, LocalDate endDate);
}
