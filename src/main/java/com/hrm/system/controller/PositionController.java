package com.hrm.system.controller;

import com.hrm.system.dto.PositionDto;
import com.hrm.system.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<List<PositionDto>> getAll() {
        return ResponseEntity.ok(positionService.getAllPosition());
    }

    // get positions by department
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<PositionDto>> getByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(positionService.getDepartmentById(departmentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PositionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(positionService.getPositionById(id));
    }

    @PostMapping
    public ResponseEntity<PositionDto> create(
            @Valid @RequestBody PositionDto dto) {
        return ResponseEntity.ok(positionService.createPosition(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PositionDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PositionDto dto) {
        return ResponseEntity.ok(positionService.UpdatePosition(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        positionService.deletePosition(id);
        return ResponseEntity.ok("Position deleted successfully");
    }
}