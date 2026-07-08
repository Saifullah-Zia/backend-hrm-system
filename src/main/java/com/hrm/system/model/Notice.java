package com.hrm.system.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Notice {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private Long userId;

    @NotBlank
    @Enumerated(EnumType.STRING)
    private NoticeType noticeType;

    @NotBlank
    private String title;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate effectiveDate;

    private String attachmentUrl;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    private Long createdBy;

    public enum NoticeType {
        TERMINATION,
        WARNING
    }
}
