package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPeriodDto {
    private Long id;
    private String month;
    private Integer year;
    private String company;
    private String department;
    private Boolean locked;
    private Long lockedBy;
    private String lockedByName;
    private LocalDateTime lockedAt;
    private Long unlockedBy;
    private LocalDateTime unlockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
