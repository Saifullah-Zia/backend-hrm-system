package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payroll_policies")
@EntityListeners(AuditingEntityListener.class)
public class PayrollPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "late_deduction_rule", columnDefinition = "TEXT")
    private String lateDeductionRule;

    @Column(name = "unpaid_leave_deduction_rule", columnDefinition = "TEXT")
    private String unpaidLeaveDeductionRule;

    @Column(name = "absent_deduction_rule", columnDefinition = "TEXT")
    private String absentDeductionRule;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description")
    private String description;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
