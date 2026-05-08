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
@Table(name = "payroll")
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month", nullable = false)
    private String month;

    @Column(name = "year")
    private Integer year;

    @Column(name = "salary")
    private Double salary = 0.0;

    @Column(name = "deduction")
    private Double deduction = 0.0;

    @Column(name = "bonuses")
    private Double bonuses = 0.0;

    @Column(name = "net_salary")
    private Double netSalary = 0.0;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";
}