package com.hrm.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "leaves")
public class Leave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Leave type is required")
    private String type; // matches LeavePolicy.leaveType

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LeaveStatus status;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    /** Computed number of working days requested — stored for quick balance ops */
    @Column(nullable = false)
    private Integer durationDays;

    private String attachmentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    public static int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }
        int count = 0;
        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            DayOfWeek dow = curr.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            curr = curr.plusDays(1);
        }
        return count;
    }

    /** Auto-calculate duration before persist / update */
    @PrePersist
    @PreUpdate
    public void calculateDuration() {
        if (startDate != null && endDate != null) {
            this.durationDays = calculateWorkingDays(startDate, endDate);
        }
    }
}