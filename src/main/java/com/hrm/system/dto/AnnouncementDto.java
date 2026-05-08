package com.hrm.system.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class AnnouncementDto {
    private Long id;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private boolean active;

    // audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}