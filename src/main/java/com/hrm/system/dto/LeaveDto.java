package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveDto {

    private Long id;
    private LocalDate startDate;
    private String leaveType;
    private LocalDate endDate;
    private String reason;
    private String status;       // APPROVED, PENDING, REJECT, CANCELLED

    private Long userId;
    private String userName;

    private Integer durationDays; // computed days for this request

    // Optional: returned during apply so the frontend can show updated balance
    private Integer remainingDaysAfterRequest;
}