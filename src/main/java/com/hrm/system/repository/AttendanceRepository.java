package com.hrm.system.repository;

import com.hrm.system.model.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Page<Attendance> findByUserId(Long userId, Pageable pageable);
    Optional<Attendance> findByUserIdAndDate(Long userId, LocalDate date);
    Optional<Attendance> findFirstByUserIdAndCheckOutIsNullOrderByCheckInDesc(Long userId);
    List<Attendance> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}