package com.hrm.system.dto;

import com.hrm.system.model.EmploymentStatus;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class EmployeeProfileDto {
    private Long id;
    private Long userId;
    private String phone;
    private String address;
    private LocalDate dateOfBirth;
    private LocalDate joiningDate;
    private String cnicNumber;
    private String profilePicture;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Long departmentId;
    private Long positionId;
    private EmploymentStatus employmentStatus;
}