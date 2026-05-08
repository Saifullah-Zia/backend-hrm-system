package com.hrm.system.repository;

import com.hrm.system.model.Payroll;
import com.hrm.system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByUserId(Long userId);

    boolean existsByUserAndMonth(User user, String month);

    Optional<Payroll> findByUserAndMonth(User user, String month);

    @Query("SELECT p FROM Payroll p WHERE p.user.id = :userId AND p.month = :month")
    Optional<Payroll> findByUserIdAndMonth(@Param("userId") Long userId, @Param("month") String month);

    List<Payroll> findByStatus(String status);

    List<Payroll> findByYear(Integer year);
}