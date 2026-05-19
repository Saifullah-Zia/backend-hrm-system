package com.hrm.system.dto;


import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.enumm.ResignationType;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ResignationDto {


    // REQUEST — employee submits resignation

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Long   employeeId;
        private LocalDate resignationDate;
        private LocalDate lastWorkingDay;
        private String reason;
        private ResignationType resignationType;
    }


    // RESPONSE returned to client

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long   id;
        private Long   employeeId;
        private String employeeName;
        private String employeeDepartment;
        private String employeePosition;

        private LocalDate resignationDate;
        private LocalDate lastWorkingDay;
        private LocalDate noticePeriodEndDate;

        private String            reason;
        private ResignationType   resignationType;
        private ResignationStatus status;

        private String  hrComments;
        private String  managerComments;
        private Boolean isNoticePeriodServed;
        private Boolean isEligibleForRehire;

        private String        approvedByName;
        private LocalDateTime approvedAt;
        private LocalDateTime createdAt;

        // Offboarding progress summary
        private int totalTasks;
        private int completedTasks;
        private int pendingTasks;
    }

    // APPROVAL REQUEST — HR approves / rejects

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalRequest {
        private ResignationStatus status;          // APPROVED or REJECTED
        private String            hrComments;
        private Boolean           isEligibleForRehire;
        private LocalDate         noticePeriodEndDate;
    }
}
