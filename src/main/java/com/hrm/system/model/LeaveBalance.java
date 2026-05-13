package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(
        name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "leave_type", "year"})
)
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String leaveType;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer totalDays;       // Total allocated for this year (including carry-forward)

    @Column(nullable = false)
    private Integer usedDays;        // Days actually consumed (approved leaves)

    @Column(nullable = false)
    private Integer pendingDays;     // Days in PENDING state

    @Column(nullable = false)
    private Integer carryForwardDays; // Days brought in from last year

    // Computed helper — not stored
    @Transient
    public int getRemainingDays() {
        return totalDays - usedDays - pendingDays;
    }
}
