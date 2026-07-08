package com.hrm.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payroll")
@EntityListeners(AuditingEntityListener.class)
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_period_id")
    @JsonIgnore
    private PayrollPeriod payrollPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "basic_salary")
    private Double basicSalary = 0.0;

    @Column(name = "daily_salary")
    private Double dailySalary = 0.0;

    @Column(name = "working_days")
    private Integer workingDays = 0;

    @Column(name = "present_days")
    private Integer presentDays = 0;

    @Column(name = "late_days")
    private Integer lateDays = 0;

    @Column(name = "paid_leave_days")
    private Integer paidLeaveDays = 0;

    @Column(name = "unpaid_leave_days")
    private Integer unpaidLeaveDays = 0;

    @Column(name = "absent_days")
    private Integer absentDays = 0;

    @Column(name = "total_allowances")
    private Double totalAllowances = 0.0;

    @Column(name = "total_bonuses")
    private Double totalBonuses = 0.0;

    @Column(name = "total_deductions")
    private Double totalDeductions = 0.0;

    @Column(name = "gross_salary")
    private Double grossSalary = 0.0;

    @Column(name = "net_salary")
    private Double netSalary = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PayrollItem> payrollItems;

    @Column(name = "generated_by")
    private Long generatedBy;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

public enum PayrollStatus {
    DRAFT,
    REVIEWED,
    APPROVED,
    PAID,
    ARCHIVED
}