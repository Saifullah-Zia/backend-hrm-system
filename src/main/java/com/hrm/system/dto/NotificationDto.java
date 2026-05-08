package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String message;
    private String type;
    private String status;
    private Long userId;
    private Long createdBy;
    private String createdByName;
    private Long referenceId;
    private LocalDateTime createdAt;
    private String timeAgo;
}