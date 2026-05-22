package com.hrm.system.service;

import com.hrm.system.dto.ChatMessageDTO;
import com.hrm.system.dto.ConversationDTO;
import com.hrm.system.dto.EmployeeSearchDTO;
import com.hrm.system.dto.MemberDTO;
import com.hrm.system.enumm.ConversationType;
import com.hrm.system.enumm.MemberRole;
import com.hrm.system.model.*;
import com.hrm.system.repository.ChatMessageRepository;
import com.hrm.system.repository.ConversationMemberRepository;
import com.hrm.system.repository.ConversationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepo;
    private final ConversationMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final ChatMessageRepository messageRepo;

    private User getUserByUsernameOrEmail(String identifier) {
        return userRepo.findByName(identifier)
                .orElseGet(() -> userRepo.findByEmail(identifier)
                        .orElseThrow(() -> new RuntimeException("User not found with identifier: " + identifier)));
    }

    // Get private chat or create if it doesn't exist
    public ConversationDTO getOrCreatePrivate(String currentEmail, Long targetId) {
        User current = getUserByUsernameOrEmail(currentEmail);
        User target  = userRepo.findById(targetId).orElseThrow();

        return conversationRepo
                .findPrivateBetween(current.getId(), target.getId())
                .map(this::toDTO)
                .orElseGet(() -> {
                    Conversation conv = new Conversation();
                    conv.setType(ConversationType.PRIVATE);
                    conv.setCreatedBy(current);
                    Conversation saved = conversationRepo.save(conv);

                    memberRepo.save(new ConversationMember(saved, current, MemberRole.ADMIN));
                    memberRepo.save(new ConversationMember(saved, target, MemberRole.MEMBER));

                    return toDTO(saved);
                });
    }

    // Create a group chat
    public ConversationDTO createGroup(String name, List<Long> memberIds, String creatorEmail) {
        User creator = getUserByUsernameOrEmail(creatorEmail);

        Conversation conv = new Conversation();
        conv.setType(ConversationType.GROUP);
        conv.setName(name);
        conv.setCreatedBy(creator);
        Conversation saved = conversationRepo.save(conv);

        // Add creator as admin
        memberRepo.save(new ConversationMember(saved, creator, MemberRole.ADMIN));

        // Add all members
        memberIds.stream()
                .map(id -> userRepo.findById(id).orElseThrow())
                .forEach(member ->
                        memberRepo.save(new ConversationMember(saved, member, MemberRole.MEMBER))
                );

        return toDTO(saved);
    }

    public List<ConversationDTO> getConversationsForUser(String email) {
        User user = getUserByUsernameOrEmail(email);
        return conversationRepo.findAllByMember(user.getId())
                .stream()
                .map(this::toDTO)
                .peek(dto -> {
                    int unread = messageRepo.countUnread(dto.getId(), user.getEmail());
                    dto.setUnreadCount(unread);
                })
                .toList();
    }

    public List<User> getOtherMembers(UUID conversationId, String excludeEmail) {
        return memberRepo.findByConversationId(conversationId)
                .stream()
                .map(ConversationMember::getEmployee)
                .filter(u -> !u.getEmail().equals(excludeEmail) && !u.getName().equals(excludeEmail))
                .toList();
    }

    // ── Search employees ─────────────────────────────────────────────────────
    public List<EmployeeSearchDTO> searchEmployees(String query, String excludeEmail) {
        return userRepo.searchByNameOrEmail(query).stream()
                .filter(u -> !u.getEmail().equals(excludeEmail) && !u.getName().equals(excludeEmail))
                .map(u -> EmployeeSearchDTO.builder()
                        .id(u.getId())
                        .fullName(u.getName())
                        .email(u.getEmail())
                        .presenceStatus(u.getPresenceStatus())
                        .build())
                .toList();
    }

    // ── Add member ───────────────────────────────────────────────────────────
    public void addMember(UUID conversationId, Long employeeId) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        User employee = userRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean alreadyMember = memberRepo
                .existsByConversationIdAndEmployeeId(conversationId, employeeId);

        if (!alreadyMember) {
            memberRepo.save(new ConversationMember(conv, employee, MemberRole.MEMBER));
        }
    }

    // ── Remove member ────────────────────────────────────────────────────────
    public void removeMember(UUID conversationId, Long employeeId, String requesterEmail) {
        User requester = getUserByUsernameOrEmail(requesterEmail);

        ConversationMember requesterMember = memberRepo
                .findByConversationIdAndEmployeeId(conversationId, requester.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this conversation"));

        if (requesterMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can remove members");
        }

        memberRepo.deleteByConversationIdAndEmployeeId(conversationId, employeeId);
    }

    public Page<ChatMessageDTO> getMessages(UUID conversationId, Pageable pageable) {
        return messageRepo
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::msgToDTO);
    }

    // ── Mappers ─────────────────────────────────────────────────────────────

    private ConversationDTO toDTO(Conversation conv) {
        List<MemberDTO> members = memberRepo.findByConversationId(conv.getId())
                .stream()
                .map(m -> MemberDTO.builder()
                        .id(m.getEmployee().getId())
                        .email(m.getEmployee().getEmail())
                        .fullName(m.getEmployee().getName())
                        .role(m.getRole().name())
                        .presenceStatus(m.getEmployee().getPresenceStatus())
                        .build())
                .toList();

        return ConversationDTO.builder()
                .id(conv.getId())
                .type(conv.getType().name())
                .name(conv.getName())
                .avatarUrl(conv.getAvatarUrl())
                .members(members)
                .createdAt(conv.getCreatedAt())
                .build();
    }

    private ChatMessageDTO msgToDTO(ChatMessage msg) {
        return ChatMessageDTO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderName(msg.getSender().getName())
                .senderEmail(msg.getSender().getEmail())
                .content(msg.getContent())
                .type(msg.getType().name())
                .fileUrl(msg.getFileUrl())
                .isRead(msg.isRead())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}