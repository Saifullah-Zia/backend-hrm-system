package com.hrm.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Column(unique = true, nullable = false)
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @Column(nullable = false)
    private boolean enabled = false;  // false until email verified

    @Column
    private String verificationCode;  // 6-digit OTP

    @Column
    private LocalDateTime verificationExpiry;

    @Column
    private String resetPasswordCode;

    @Column
    private LocalDateTime resetPasswordExpiry;

    @Column(name = "basic_salary")
    private Double basicSalary;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String presenceStatus = "offline";
    private LocalDateTime lastSeenAt;

    // Relationships — @JsonIgnore prevents infinite recursion & lazy-load errors
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Attendance> attendances;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Leave> leaves;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Payroll> payrolls;

    @OneToMany(mappedBy = "lockedBy", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<PayrollPeriod> lockedPayrollPeriods;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<AttendanceSummary> attendanceSummaries;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<LeaveBalance> leaveBalances;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private EmployeeProfile employeeProfile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<AttendanceCorrectionRequest> attendanceCorrectionRequests;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ChatMessage> chatMessages;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ConversationMember> conversationMembers;

    // AuditLogs are preserved when user is deleted (performedBy is nullified instead of cascade)
    @OneToMany(mappedBy = "performedBy")
    @JsonIgnore
    private List<AuditLog> auditLogs;

    // OffboardingTask is cascade-deleted via EmployeeProfile → do NOT duplicate orphanRemoval here
    @OneToMany(mappedBy = "assignedTo")
    @JsonIgnore
    private List<OffboardingTask> offboardingTasks;

    // ─── Probation fields ─────────────────────────────────────────────────
    @Column
    private LocalDate probationStartDate;

    @Column
    private LocalDate probationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ProbationStatus probationStatus;

    @Column(columnDefinition = "boolean default false")
    private Boolean probationNotificationSent = false;

    // Audit fields
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