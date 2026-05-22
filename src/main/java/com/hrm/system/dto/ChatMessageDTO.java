package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private UUID id;
    private UUID conversationId;
    private String senderName;
    private String senderEmail;
    private String content;
    private String type;        // "TEXT", "FILE", "IMAGE"
    private String fileUrl;
    private boolean isRead;
    private LocalDateTime createdAt;
}