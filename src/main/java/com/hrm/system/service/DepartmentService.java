package com.hrm.system.service;

import com.hrm.system.dto.DepartmentDto;
import com.hrm.system.model.Department;
import com.hrm.system.model.Position;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.repository.DepartmentRepository;
import com.hrm.system.repository.PositionRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

    //convert to dto
    public DepartmentDto toDto (Department dept){
        DepartmentDto dto = new DepartmentDto();
        dto.setId(dept.getId());
        dto.setName(dept.getName());
        dto.setDescription(dept.getDescription());
        return dto;
    }

    //convert dto to entity
    public Department toEntity(DepartmentDto dto){
        Department dept = new Department();
        dept.setName(dto.getName());
        dept.setDescription(dto.getDescription());
        return dept;
    }

    //get all department
    public List<DepartmentDto> getAllDepartment(){
        return departmentRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    //get department by id
    public DepartmentDto getDepartmentById(Long id){
        Department dept =departmentRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Department not found on this id"));
        return toDto(dept);
    }

    //create department
    public DepartmentDto create(DepartmentDto dto) {
        if (departmentRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Department already exist on this name");
        }
        Department dept = toEntity(dto);
        return toDto(departmentRepository.save(dept));
    }

    //Update Department
    public  DepartmentDto updateDepartment(Long id, DepartmentDto dto){
        Department dept =departmentRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Department not found"));
        dept.setName(dto.getName());
        dept.setDescription(dto.getDescription());
        return toDto(departmentRepository.save(dept));
    }

    //delete department
    @Transactional
    public void deleteDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // 1. Unlink all Positions referencing this Department
        List<Position> positions = positionRepository.findByDepartmentId(id);
        if (positions != null) {
            for (Position position : positions) {
                position.setDepartment(null);
                positionRepository.save(position);
            }
        }

        // 2. Unlink all EmployeeProfiles referencing this Department
        if (dept.getEmployee() != null) {
            for (EmployeeProfile emp : new java.util.ArrayList<>(dept.getEmployee())) {
                emp.setDepartment(null);
                employeeProfileRepository.save(emp);
            }
            dept.getEmployee().clear();
        }

        // 3. Delete the department
        departmentRepository.delete(dept);
    }

}
