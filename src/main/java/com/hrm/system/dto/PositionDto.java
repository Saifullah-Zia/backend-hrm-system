package com.hrm.system.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PositionDto {
    private Long id;

    @NotBlank
    private String title;
    private String description;
    private Long departmentId;
}