package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HikvisionEventDto {
    private Integer employeeId;  // Device Employee ID (maps to biometricPersonId)
    private String eventType;   // e.g., "Authenticated via Fingerprint"
    private String timestamp;   // Format: "2026-07-03 05:14:18" (YYYY-MM-DD HH:MM:SS)
}
