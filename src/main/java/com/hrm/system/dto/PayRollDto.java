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
}