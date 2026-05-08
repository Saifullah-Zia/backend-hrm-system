package com.hrm.system.controller;

import com.hrm.system.dto.DepartmentDto;
import com.hrm.system.repository.DepartmentRepository;
import com.hrm.system.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    //get all departments
    @GetMapping
    public ResponseEntity<List<DepartmentDto>> getAllDepartments(){
        return ResponseEntity.ok(departmentService.getAllDepartment());
    }

    //get department by id
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDto> getDepartmentById(@PathVariable Long id){
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    //Create Department
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> createDept(@Valid @RequestBody DepartmentDto dto){
        return ResponseEntity.ok(departmentService.create(dto));
    }

    //Update Department
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> updateDept(@PathVariable  Long id, @RequestBody DepartmentDto dto){
        return ResponseEntity.ok(departmentService.updateDepartment(id, dto));
    }

    //Delete Department
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok("Deleted successfully");
    }

}
