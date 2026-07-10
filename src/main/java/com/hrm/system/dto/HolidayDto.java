package com.hrm.system.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayDto {
    private Long id;
    private String name;
    private LocalDate date;
    private Boolean isRecurring;
    private Boolean isActive;
}
