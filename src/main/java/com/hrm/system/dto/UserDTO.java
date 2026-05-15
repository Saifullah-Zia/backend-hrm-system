package com.hrm.system.dto;

import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private Role role;

    // probation
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private ProbationStatus probationStatus;
}