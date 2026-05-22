package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSearchDTO {
    private Long id;
    private String fullName;
    private String email;
    private String department;
    private String avatarUrl;
    private String presenceStatus;
}