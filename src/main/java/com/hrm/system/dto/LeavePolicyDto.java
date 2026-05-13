package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeavePolicyDto {

    private Long id;
    private String leaveType;
    private Integer totalDaysPerYear;
    private Boolean requiresOneYear;
    private Boolean carryForward;
    private Integer maxCarryForwardDays;
    private Boolean isPublicHoliday;
    private Integer applyBeforeDays; // null for non-holiday types
}