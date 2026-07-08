package com.hrm.system.dto;

import com.hrm.system.model.PayrollItemType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollItemDto {
    private Long id;
    private Long payrollId;
    private PayrollItemType type;
    private String name;
    private Double amount;
    private String description;
}
