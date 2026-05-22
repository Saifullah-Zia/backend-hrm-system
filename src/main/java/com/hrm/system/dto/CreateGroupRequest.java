package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {
    private String name;
    private List<Long> memberIds;   // Long not UUID — matches User.id
}