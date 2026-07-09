package com.hrm.system.model;

import jakarta.persistence.*;
import jakarta.persistence.Id;
import lombok.*;
import com.hrm.system.model.Position;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "employee_profiles")
@EntityListeners(AuditingEntityListener.class)
public class EmployeeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // Added firstName and lastName fields
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phone;
    private String address;
    private LocalDate dateOfBirth;
    private LocalDate joiningDate;
    private String cnicNumber;

    @Column(columnDefinition = "TEXT")
    private String profilePicture;
    // store file path or URL
    private String emergencyContactName;
    private String emergencyContactPhone;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus; // ACTIVE, INACTIVE, TERMINATED

    @Column(name = "biometric_person_id")
    private Integer biometricPersonId; // Hikvision device Employee ID

    @Column(name = "basic_salary")
    private Double basicSalary; // Base monthly salary used for payroll generation

    @OneToMany(mappedBy = "employeeProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Resignation> resignations;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<OffboardingTask> offboardingTasks;

    @OneToMany(mappedBy = "employeeProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Document> documents;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;
}