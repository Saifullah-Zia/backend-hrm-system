package com.hrm.system.controller;

import com.hrm.system.dto.OfficeHoursDto;
import com.hrm.system.service.OfficeHoursService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/office-hours")
public class OfficeHoursController {

    private final OfficeHoursService officeHoursService;

    public OfficeHoursController(OfficeHoursService officeHoursService) {
        this.officeHoursService = officeHoursService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERADMIN')")
    public ResponseEntity<OfficeHoursDto> getOfficeHours() {
        return ResponseEntity.ok(officeHoursService.get());
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERADMIN')")
    public ResponseEntity<OfficeHoursDto> updateOfficeHours(@RequestBody OfficeHoursDto dto) {
        return ResponseEntity.ok(officeHoursService.save(dto));
    }
}
