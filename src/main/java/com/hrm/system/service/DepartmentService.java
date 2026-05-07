package com.hrm.system.service;

import com.hrm.system.dto.DepartmentDto;
import com.hrm.system.model.Department;
import com.hrm.system.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

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
    public  void deleteDepartment(Long id){
        if(!departmentRepository.existsById(id)){
            throw new RuntimeException("department not found");
        }
        departmentRepository.deleteById(id);
    }

}
