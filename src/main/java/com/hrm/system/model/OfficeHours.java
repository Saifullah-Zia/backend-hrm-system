package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "office_hours")
public class OfficeHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workday_start", nullable = false)
    private String workdayStart;

    @Column(name = "workday_end", nullable = false)
    private String workdayEnd;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;
}