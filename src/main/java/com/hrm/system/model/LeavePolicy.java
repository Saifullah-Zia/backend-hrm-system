package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "leave_policies")
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @Column (unique = true, nullable = false)
    private  String leaveType;

    @Column(nullable = false)
    private Integer totalDaysPerYear;

    @Column(nullable = false)
    private Boolean requiresOneYear;

    @Column(nullable = false)
    private Boolean carryForward; // Can unused leaves roll over?

    @Column(nullable = false)
    private Integer maxCarryForwardDays;

    @Column(nullable = false)
    private Boolean isPublicHoliday; // e.g. Eid leaves

    /**
     * For public holidays (Eid), how many days before the event can an employee apply?
     * Null for non-holiday types.
     */
    private Integer applyBeforeDays;

}
