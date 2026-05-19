package com.hrm.system.dto;

import com.hrm.system.model.ProbationStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProbationDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long userId;
        private String name;
        private String email;
        private ProbationStatus probationStatus;
        private LocalDate probationStartDate;
        private LocalDate probationEndDate;
        private Boolean probationNotificationSent;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmRequest {
        private Long confirmedByAdminId;
    }
}