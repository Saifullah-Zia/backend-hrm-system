package com.hrm.system.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class DepartmentDto {
    private Long id;

    @NotBlank
    private String name;
    private String description;
}