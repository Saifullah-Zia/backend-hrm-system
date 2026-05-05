package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDto {

    private Long id;
    private LocalDate date;
    private String status;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private Long userId;
}