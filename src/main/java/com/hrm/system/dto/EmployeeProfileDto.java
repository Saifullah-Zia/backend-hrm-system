package com.hrm.system.dto;

import com.hrm.system.model.EmploymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class EmployeeProfileDto {
    private Long id;

    @NotNull(message = "Employee user id is required")
    private Long userId;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;
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
    private Integer biometricPersonId; // Hikvision device Employee ID
    private Double basicSalary; // Base monthly salary used for payroll generation

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}