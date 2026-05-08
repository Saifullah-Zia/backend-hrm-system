package com.hrm.system.controller;

import com.hrm.system.dto.EmployeeProfileDto;
import com.hrm.system.service.EmployeeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/employee-profiles")
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final EmployeeProfileService employeeProfileService;

    @GetMapping
    public ResponseEntity<List<EmployeeProfileDto>> getAll() {
        return ResponseEntity.ok(employeeProfileService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeProfileDto> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(employeeProfileService.getById(id));
    }

    // get profile by user id
    @GetMapping("/user/{userId}")
    public ResponseEntity<EmployeeProfileDto> getByUserId(
            @PathVariable Long userId) {
        return ResponseEntity.ok(employeeProfileService.getByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<EmployeeProfileDto> create(
            @Valid @RequestBody EmployeeProfileDto dto) {
        return ResponseEntity.ok(employeeProfileService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeProfileDto> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeProfileDto dto) {
        return ResponseEntity.ok(employeeProfileService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        employeeProfileService.delete(id);
        return ResponseEntity.ok("Employee profile deleted successfully");
    }
}