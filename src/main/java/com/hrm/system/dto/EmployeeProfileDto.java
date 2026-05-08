package com.hrm.system.dto;

import com.hrm.system.model.EmploymentStatus;
import jakarta.persistence.Column;
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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}