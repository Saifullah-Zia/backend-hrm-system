package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceDto {

    private Long id;
    private Long userId;
    private String userName;
    private String leaveType;
    private Integer year;
    private Integer totalDays;
    private Integer usedDays;
    private Integer pendingDays;
    private Integer remainingDays;
    private Integer carryForwardDays;
}