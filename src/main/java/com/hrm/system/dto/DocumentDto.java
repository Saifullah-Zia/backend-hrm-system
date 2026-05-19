package com.hrm.system.dto;

import com.hrm.system.enumm.DocumentStatus;
import com.hrm.system.enumm.DocumentType;
import lombok.*;

import java.time.LocalDateTime;

public class DocumentDto {


        // REQUEST used when uploading a new document

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Request {

            private Long employeeId;        // target employee

            private String title;           // document display title

            private DocumentType documentType;

            private String description;     // optional notes

            private LocalDateTime expiryDate; // null if no expiry
        }

        // RESPONSE — returned to the client

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Response {

            private Long id;

            private Long employeeId;
            private String employeeName;    // full name for display

            private String title;
            private String fileName;
            private String fileType;
            private Long fileSize;          // bytes
            private String fileSizeFormatted; // e.g. "1.2 MB"

            private DocumentType documentType;
            private DocumentStatus status;

            private String description;
            private LocalDateTime expiryDate;
            private boolean isExpired;      // computed flag

            private String uploadedByName;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;
        }

        // UPDATE REQUEST — used when editing metadata

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class UpdateRequest {

            private String title;
            private DocumentType documentType;
            private String description;
            private LocalDateTime expiryDate;
            private DocumentStatus status;
        }
    }

