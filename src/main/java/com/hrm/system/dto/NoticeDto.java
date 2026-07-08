package com.hrm.system.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private Object userId;

    private String employeeName;

    @NotBlank
    private String noticeType;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    private LocalDate effectiveDate;
    private String attachmentUrl;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String createdByName;
}