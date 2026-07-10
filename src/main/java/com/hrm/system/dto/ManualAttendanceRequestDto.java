package com.hrm.system.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ManualAttendanceRequestDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Long> userIds; // null or empty = all tracked employees
}