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

    // No @GeneratedValue: this table is a fixed singleton settings row.
    // OfficeHoursService.save() always assigns id = SETTINGS_ID (1L) itself,
    // so the ID must NOT be database-generated (GenerationType.IDENTITY),
    // otherwise Hibernate can't reconcile the manually-set id with the
    // IDENTITY strategy and the insert silently never happens.
    @Id
    private Long id;

    @Column(name = "workday_start", nullable = false)
    private String workdayStart;

    @Column(name = "workday_end", nullable = false)
    private String workdayEnd;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;
}