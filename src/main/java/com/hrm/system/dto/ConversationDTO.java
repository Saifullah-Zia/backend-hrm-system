package com.hrm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private UUID id;
    private String type;
    private String name;
    private String avatarUrl;
    private List<MemberDTO> members;       // ← this must exist
    private ChatMessageDTO lastMessage;
    private int unreadCount;
    private LocalDateTime createdAt;
}