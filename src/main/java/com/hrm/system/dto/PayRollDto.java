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
    private Integer holidayDays;        // paid public holidays
    private Double totalAllowances;
    private Double totalBonuses;
    private Double totalDeductions;
    private Double lateDeduction;
    private Double fbrTaxDeduction;
    private Double unpaidLeaveDeduction;
    private Double absentDeduction;
    private Double autoFbrTax;          // what the tax rules would charge, shown next to an override
    private Double fbrTaxOverride;      // null = auto-calculated
    private Boolean useAutoFbrTax;      // update only: true clears the override
    private Double manualDeduction;
    private String deductionNote;
    private Double grossSalary;
    private Long generatedBy;
    private java.time.LocalDateTime generatedAt;
    private Long approvedBy;
    private java.time.LocalDateTime approvedAt;
    private java.time.LocalDateTime paidAt;
}