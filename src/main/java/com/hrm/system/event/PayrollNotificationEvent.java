package com.hrm.system.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Builder
@ToString
public class PayrollNotificationEvent {

    public enum NotificationType {
        GENERATED,
        APPROVED
    }

    private final NotificationType type;
    private final Long payrollId;
    private final Long employeeId;
    private final String employeeName;
    private final String employeeEmail;
    private final String month;
    private final Integer year;
    private final BigDecimal netSalary;
}
