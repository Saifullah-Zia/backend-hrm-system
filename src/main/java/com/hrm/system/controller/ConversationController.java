package com.hrm.system.controller;

import com.hrm.system.dto.ChatMessageDTO;
import com.hrm.system.dto.ConversationDTO;
import com.hrm.system.dto.CreateGroupRequest;
import com.hrm.system.dto.EmployeeSearchDTO;
import com.hrm.system.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    // Get or start a private chat between 2 employees
    @PostMapping("/private")
    public ResponseEntity<ConversationDTO> startPrivateChat(
            @RequestParam Long targetEmployeeId,
            Principal principal) {
        return ResponseEntity.ok(
                conversationService.getOrCreatePrivate(principal.getName(), targetEmployeeId)
        );
    }

    // Create a group chat
    @PostMapping("/group")
    public ResponseEntity<ConversationDTO> createGroup(
            @RequestBody CreateGroupRequest req,
            Principal principal) {
        return ResponseEntity.ok(
                conversationService.createGroup(req.getName(), req.getMemberIds(), principal.getName())
        );
    }

    // Add member to group
    @PostMapping("/group/{conversationId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable UUID conversationId,
            @RequestParam Long employeeId) {
        conversationService.addMember(conversationId, employeeId);
        return ResponseEntity.ok().build();
    }

    // Remove member from group
    @DeleteMapping("/group/{conversationId}/members/{employeeId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID conversationId,
            @PathVariable Long employeeId,
            Principal principal) {
        conversationService.removeMember(conversationId, employeeId, principal.getName());
        return ResponseEntity.ok().build();
    }

    // Get all my conversations (private + groups)
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> getMyConversations(Principal principal) {
        return ResponseEntity.ok(
                conversationService.getConversationsForUser(principal.getName())
        );
    }

    // Load message history (paginated)
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Page<ChatMessageDTO>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
                conversationService.getMessages(conversationId,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
        );
    }

    // Search employees to start a chat
    @GetMapping("/employees/search")
    public ResponseEntity<List<EmployeeSearchDTO>> searchEmployees(
            @RequestParam String query,
            Principal principal) {
        return ResponseEntity.ok(
                conversationService.searchEmployees(query, principal.getName())
        );
    }
}