package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long payrollPeriodId;
    private Integer presentDays;
    private Integer lateDays;
    private Integer paidLeaveDays;
    private Integer unpaidLeaveDays;
    private Integer absentDays;
    private Integer workingDays;
    private LocalDateTime createdAt;
}
