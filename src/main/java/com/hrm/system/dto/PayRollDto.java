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
        private String month; // stored as "YYYY-MM" string
        private double salary;
        private double deductions;
        private double netSalary;
        private double bonuses;
    }

