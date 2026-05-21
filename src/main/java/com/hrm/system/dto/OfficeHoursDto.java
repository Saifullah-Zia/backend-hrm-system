package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfficeHoursDto {
    private String workdayStart;
    private String workdayEnd;
    private int graceMinutes;
    private String timezone;

    // Constructor without timezone for backward compatibility
    public OfficeHoursDto(String workdayStart, String workdayEnd, int graceMinutes) {
        this.workdayStart = workdayStart;
        this.workdayEnd   = workdayEnd;
        this.graceMinutes = graceMinutes;
        this.timezone     = "Asia/Karachi";
    }

}

