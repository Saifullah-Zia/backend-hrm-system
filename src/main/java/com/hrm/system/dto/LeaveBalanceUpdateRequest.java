package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceUpdateRequest {

    private Integer totalDays;
    private Integer usedDays;
    private Integer pendingDays;
    private Integer carryForwardDays;
}
