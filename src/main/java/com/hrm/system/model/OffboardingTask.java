package com.hrm.system.model;


import com.hrm.system.enumm.OffboardingTaskCategory;
import com.hrm.system.enumm.OffboardingTaskStatus;
import jakarta.persistence.*;
        import lombok.*;
        import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "offboarding_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffboardingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Linked to a resignation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resignation_id", nullable = false)
    private Resignation resignation;

    // Which employee this task belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeProfile employee;

    @Column(nullable = false)
    private String taskName;            // e.g. "Return Laptop"

    private String taskDescription;     // additional details

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OffboardingTaskCategory category; // IT, HR, FINANCE, ADMIN …

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OffboardingTaskStatus taskStatus = OffboardingTaskStatus.PENDING;

    private LocalDate dueDate;          // when task must be completed

    private LocalDate completedDate;    // when it was actually completed

    // Who is responsible for completing this task
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    private String remarks;             // completion notes

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}