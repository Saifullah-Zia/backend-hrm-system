package com.hrm.system.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class AnnouncementDto {
    private Long id;

    @NotBlank
    private String title;

    @NotBlank
    private String content;
    private boolean active;
}