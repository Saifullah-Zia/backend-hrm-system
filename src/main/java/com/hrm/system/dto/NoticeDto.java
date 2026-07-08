package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeDto {
    private Long id;
    private Long userId;
    private String employeeName;
    private String noticeType;
    private String title;
    private String description;
    private LocalDate effectiveDate;
    private String attachmentUrl;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String createdByName;
}
