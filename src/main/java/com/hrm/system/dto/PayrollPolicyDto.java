package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPolicyDto {
    private Long id;
    private String lateDeductionRule;
    private String unpaidLeaveDeductionRule;
    private String absentDeductionRule;
    private Boolean isActive;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
