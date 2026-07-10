package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ManualAttendanceResultDto {
    private int created;
    private int updatedToLeave;
    private int skippedAlreadyHandled;
    private int skippedWeekend;
}