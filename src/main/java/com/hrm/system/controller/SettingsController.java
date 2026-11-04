package com.hrm.system.controller;

import com.hrm.system.dto.OfficeHoursDto;
import com.hrm.system.service.OfficeHoursService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OfficeHoursService officeHoursService;

    @Value("${office.allowed.ips:58.65.129.12}")
    private String allowedOfficeIps;

    public SettingsController(OfficeHoursService officeHoursService) {
        this.officeHoursService = officeHoursService;
    }

    @GetMapping("/office-hours")
    public ResponseEntity<OfficeHoursDto> getOfficeHours(){
        return ResponseEntity.ok(officeHoursService.get());
    }

    @PutMapping("/office-hours")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<OfficeHoursDto> updateOfficeHours(@RequestBody OfficeHoursDto dto){
        return ResponseEntity.ok(officeHoursService.save(dto));
    }

    @GetMapping("/my-ip")
    public ResponseEntity<Map<String, Object>> getMyIp(HttpServletRequest request) {
        String clientIp = com.hrm.system.util.IpUtil.getClientIp(request);
        // Public endpoint: says whether the caller is on the office network, never what that network is.
        return ResponseEntity.ok(Map.of(
                "resolvedClientIp", clientIp,
                "onOfficeNetwork", com.hrm.system.util.IpUtil.isAllowedIp(clientIp, allowedOfficeIps, false)));
    }
}
