package com.hrm.system.repository;

import com.hrm.system.model.AttendanceCorrectionRequest;
import com.hrm.system.model.CorrectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface AttendanceCorrectionRequestRepository extends JpaRepository<AttendanceCorrectionRequest, Long> {
    List<AttendanceCorrectionRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AttendanceCorrectionRequest> findByStatusOrderByCreatedAtDesc(CorrectionStatus status);
    Optional<AttendanceCorrectionRequest> findByUserIdAndDate(Long userId, LocalDate date);
}
