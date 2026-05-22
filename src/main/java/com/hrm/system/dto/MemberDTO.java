package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {
    private Long id;           // Long not UUID — matches User.id
    private String email;
    private String fullName;
    private String role;
    private String presenceStatus;
}