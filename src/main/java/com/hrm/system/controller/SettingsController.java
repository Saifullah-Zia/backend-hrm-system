package com.hrm.system.controller;

import com.hrm.system.dto.OfficeHoursDto;
import com.hrm.system.service.OfficeHoursService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OfficeHoursService officeHoursService;

    public SettingsController(OfficeHoursService officeHoursService) {
        this.officeHoursService = officeHoursService;
    }

    @GetMapping("/office-hours")
    public ResponseEntity<OfficeHoursDto> getOfficeHours(){
        return ResponseEntity.ok(officeHoursService.get());
    }

    @PutMapping("/office-hours")
    @PreAuthorize("hasRole (ADMIN) or hasRole(SUPERADMIN)")
    public ResponseEntity<OfficeHoursDto> updateOfficeHours(@RequestBody OfficeHoursDto dto){
        return ResponseEntity.ok(officeHoursService.save(dto));
    }
}
