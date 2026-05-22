package com.hrm.system.service;

import com.hrm.system.dto.ChatMessageDTO;
import com.hrm.system.dto.ChatMessageRequest;
import com.hrm.system.enumm.MessageType;                  // ← correct
import com.hrm.system.model.ChatMessage;
import com.hrm.system.model.Conversation;
import com.hrm.system.model.User;
import com.hrm.system.repository.ChatMessageRepository;
import com.hrm.system.repository.ConversationMemberRepository;
import com.hrm.system.repository.ConversationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepo;
    private final ConversationMemberRepository memberRepo;
    private final ConversationRepository conversationRepo;   // ← added
    private final UserRepository userRepo;

    private User getUserByUsernameOrEmail(String identifier) {
        return userRepo.findByName(identifier)
                .orElseGet(() -> userRepo.findByEmail(identifier)
                        .orElseThrow(() -> new RuntimeException("User not found with identifier: " + identifier)));
    }

    public ChatMessageDTO save(UUID conversationId, String senderEmail,
                               ChatMessageRequest req) {

        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        User sender = getUserByUsernameOrEmail(senderEmail);

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(req.getContent());
        message.setType(req.getMessageType() != null ? req.getMessageType() : MessageType.TEXT);  // ← fixed
        message.setFileUrl(req.getFileUrl());
        message.setCreatedAt(LocalDateTime.now());

        return toDTO(messageRepo.save(message));
    }

    public void markAllRead(UUID conversationId, String email) {
        User user = getUserByUsernameOrEmail(email);
        messageRepo.markAllReadInConversation(conversationId, user.getEmail());
    }

    public void updatePresence(String email, String status) {
        User user = getUserByUsernameOrEmail(email);
        userRepo.updatePresenceStatus(user.getEmail(), status, LocalDateTime.now());
    }

    public int getUnreadCount(UUID conversationId, String email) {
        User user = getUserByUsernameOrEmail(email);
        return messageRepo.countUnread(conversationId, user.getEmail());
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    public ChatMessageDTO toDTO(ChatMessage msg) {
        return ChatMessageDTO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderName(msg.getSender().getName())       // getName() not getUsername()
                .senderEmail(msg.getSender().getEmail())
                .content(msg.getContent())
                .type(msg.getType().name())
                .fileUrl(msg.getFileUrl())
                .isRead(msg.isRead())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}