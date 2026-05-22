package com.hrm.system.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatNotification {
    private UUID conversationId;
    private String senderName;
    private String preview;   // first 50 chars of message

    public ChatNotification(ChatMessageDTO msg) {
        this.conversationId = msg.getConversationId();
        this.senderName     = msg.getSenderName();
        this.preview        = msg.getContent() != null
                ? msg.getContent().substring(0, Math.min(50, msg.getContent().length()))
                : "Sent a file";
    }
}