package com.hrm.system.controller;

import com.hrm.system.dto.OfficeHoursDto;
import com.hrm.system.service.OfficeHoursService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OfficeHoursService officeHoursService;

    @Value("${office.allowed.ips:58.65.129.12,127.0.0.1,0:0:0:0:0:0:0:1}")
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
    public ResponseEntity<Map<String, String>> getMyIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        String clientIp = (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();

        Map<String, String> result = new HashMap<>();
        result.put("clientIp", clientIp);
        result.put("configuredOfficeIps", allowedOfficeIps);
        return ResponseEntity.ok(result);
    }
}
