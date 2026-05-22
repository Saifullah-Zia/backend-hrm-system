package com.hrm.system.controller;

import com.hrm.system.dto.ChatMessageDTO;
import com.hrm.system.dto.ChatMessageRequest;
import com.hrm.system.dto.ChatNotification;
import com.hrm.system.dto.PresenceEvent;
import com.hrm.system.dto.TypingEvent;
import com.hrm.system.service.ChatMessageService;
import com.hrm.system.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate broker;

    // ── Send a message ──────────────────────────────────────────
    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload ChatMessageRequest req,
            Principal principal) {

        ChatMessageDTO saved = chatMessageService.save(
                conversationId, principal.getName(), req);

        // Push to all members of this conversation
        broker.convertAndSend(
                "/topic/conversation/" + conversationId,
                (Object) saved);

        // Push notification to each other member
        conversationService.getOtherMembers(conversationId, principal.getName())
                .forEach(member ->
                        broker.convertAndSendToUser(
                                member.getEmail(),               // email not username
                                "/queue/notifications",
                                (Object) new ChatNotification(saved)
                        )
                );
    }

    // ── Typing indicator ────────────────────────────────────────
    @MessageMapping("/chat.typing/{conversationId}")
    public void typing(
            @DestinationVariable UUID conversationId,
            @Payload TypingEvent event,
            Principal principal) {

        event.setEmployeeName(principal.getName());
        broker.convertAndSend(
                "/topic/typing/" + conversationId,
                (Object) event);
    }

    // ── Mark messages as read ───────────────────────────────────
    @MessageMapping("/chat.read/{conversationId}")
    public void markRead(
            @DestinationVariable UUID conversationId,
            Principal principal) {

        chatMessageService.markAllRead(conversationId, principal.getName());

        // cast to Object to resolve ambiguous overload
        Map<String, String> payload = Map.of("reader", principal.getName());
        broker.convertAndSend(
                "/topic/read/" + conversationId,
                (Object) payload);
    }

    // ── Presence (online/offline) ───────────────────────────────
    @MessageMapping("/presence")
    public void presence(
            @Payload PresenceEvent event,
            Principal principal) {

        event.setEmployeeId(principal.getName());
        chatMessageService.updatePresence(principal.getName(), event.getStatus());
        broker.convertAndSend(
                "/topic/presence",
                (Object) event);
    }
}