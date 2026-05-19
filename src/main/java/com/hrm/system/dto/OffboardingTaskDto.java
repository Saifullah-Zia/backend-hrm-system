package com.hrm.system.dto;


import com.hrm.system.enumm.OffboardingTaskCategory;
import com.hrm.system.enumm.OffboardingTaskStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OffboardingTaskDto {

    // ─────────────────────────────────────────────
    // REQUEST — create a new offboarding task
    // ─────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Long                    resignationId;
        private String                  taskName;
        private String                  taskDescription;
        private OffboardingTaskCategory category;
        private LocalDate               dueDate;
        private Long                    assignedToUserId;
    }

    // RESPONSE — returned to client

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long                    id;
        private Long                    resignationId;
        private Long                    employeeId;
        private String                  employeeName;

        private String                  taskName;
        private String                  taskDescription;
        private OffboardingTaskCategory category;
        private OffboardingTaskStatus taskStatus;

        private LocalDate               dueDate;
        private LocalDate               completedDate;
        private boolean                 isOverdue;      // computed

        private String                  assignedToName;
        private String                  remarks;
        private LocalDateTime           createdAt;
    }


    // UPDATE REQUEST — mark task complete / update

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private OffboardingTaskStatus taskStatus;
        private LocalDate             completedDate;
        private String                remarks;
    }
}