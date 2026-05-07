package com.hrm.system.repository;

import com.hrm.system.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByName(String name);
}