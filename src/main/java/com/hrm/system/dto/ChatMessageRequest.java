package com.hrm.system.dto;

import com.hrm.system.enumm.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    private String content;
    private MessageType messageType;   // renamed from 'type' to avoid conflict
    private String fileUrl;
}