package com.hrm.system.model;

import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.enumm.ResignationType;
import jakarta.persistence.*;
        import lombok.*;
        import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resignations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Employee who is resigning
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_profile_id", nullable = false)
    private EmployeeProfile employeeProfile;

    @Column(nullable = false)
    private LocalDate resignationDate;      // date letter was submitted

    @Column(nullable = false)
    private LocalDate lastWorkingDay;       // last day in office

    private LocalDate noticePeriodEndDate;  // calculated from notice period

    @Column(nullable = false)
    private String reason;                  // reason for leaving

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ResignationStatus status = ResignationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private ResignationType resignationType; // VOLUNTARY, INVOLUNTARY, RETIREMENT etc.

    private String hrComments;              // HR notes / remarks

    private String managerComments;         // manager notes

    private Boolean isNoticePeriodServed;   // did employee serve notice?

    private Boolean isEligibleForRehire;    // rehire eligibility flag

    // Who approved / rejected the resignation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}