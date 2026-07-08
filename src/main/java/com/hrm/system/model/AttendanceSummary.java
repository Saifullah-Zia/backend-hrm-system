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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance_summaries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "payroll_period_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class AttendanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnore
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    @JsonIgnore
    private PayrollPeriod payrollPeriod;

    @Column(name = "present_days", nullable = false)
    private Integer presentDays = 0;

    @Column(name = "late_days", nullable = false)
    private Integer lateDays = 0;

    @Column(name = "paid_leave_days", nullable = false)
    private Integer paidLeaveDays = 0;

    @Column(name = "unpaid_leave_days", nullable = false)
    private Integer unpaidLeaveDays = 0;

    @Column(name = "absent_days", nullable = false)
    private Integer absentDays = 0;

    @Column(name = "working_days", nullable = false)
    private Integer workingDays = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
