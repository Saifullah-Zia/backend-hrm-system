package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Payroll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @Column(name = "month")
    private String month; // stored as "YYYY-MM" string
    private Double salary;
    private Double deduction;
    private Double bonuses;
    private double netSalary;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
