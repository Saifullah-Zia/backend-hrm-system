package com.hrm.system.repository;
import com.hrm.system.model.EmployeeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.util.Optional;
public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, Long> {
    Optional<EmployeeProfile> findByUserId(Long userId);
    @Query("SELECT e FROM EmployeeProfile e JOIN FETCH e.user WHERE e.id = :id")
    Optional<EmployeeProfile> findByIdWithUser(@Param("id") Long id);

    @Query("""
    SELECT e FROM EmployeeProfile e
    WHERE (:search IS NULL
           OR LOWER(e.firstName) LIKE :search
           OR LOWER(e.lastName)  LIKE :search
           OR LOWER(e.user.email) LIKE :search)
      AND (:departmentId IS NULL OR e.department.id = :departmentId)
    """)
    Page<EmployeeProfile> findAllPaged(
            @Param("search")       String search,
            @Param("departmentId") Long departmentId,
            Pageable pageable);

}