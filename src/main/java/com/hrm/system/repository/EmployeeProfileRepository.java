package com.hrm.system.repository;
import com.hrm.system.model.EmployeeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, Long> {
    Optional<EmployeeProfile> findByUserId(Long userId);
}