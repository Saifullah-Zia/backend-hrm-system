package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayRollDto {
    private Long id;
    private Long userId;
    private String userName;
    private String month;
    private Double salary;      // Changed from double to Double
    private Double bonuses;     // Changed from double to Double
    private Double deductions;  // Changed from double to Double
    private Double netSalary;   // Changed from double to Double
    private String status;

    // Breakdown fields
    private Double basicSalary;
    private Double dailySalary;
    private Integer workingDays;
    private Integer presentDays;
    private Integer lateDays;
    private Integer paidLeaveDays;
    private Integer unpaidLeaveDays;
    private Integer absentDays;
    private Double totalAllowances;
    private Double totalBonuses;
    private Double totalDeductions;
    private Double grossSalary;
    private Long generatedBy;
    private java.time.LocalDateTime generatedAt;
    private Long approvedBy;
    private java.time.LocalDateTime approvedAt;
    private java.time.LocalDateTime paidAt;
}