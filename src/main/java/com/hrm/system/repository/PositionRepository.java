package com.hrm.system.repository;
import com.hrm.system.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByDepartmentId(Long departmentId);
}