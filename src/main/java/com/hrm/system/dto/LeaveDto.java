package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDto {

    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String status; // APPROVED, PENDING, REJECTED
    private Long userId;   // reference to user
}