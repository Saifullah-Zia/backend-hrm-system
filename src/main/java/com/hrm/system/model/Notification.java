package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String type;  // LEAVE_REQUEST, LEAVE_APPROVED, LEAVE_REJECTED, PAYROLL

    @Column(nullable = false)
    private String status;  // UNREAD, READ

    @Column(name = "user_id", nullable = false)
    private Long userId;  // Who receives the notification

    @Column(name = "created_by", nullable = false)
    private Long createdBy;  // Who triggered the notification

    @Column(name = "reference_id")
    private Long referenceId;  // Leave ID or Payroll ID

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = "UNREAD";
    }
}