package com.hrm.system.model;

import com.hrm.system.enumm.MemberRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversation_members")
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // ← THIS was missing
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private User employee;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    private LocalDateTime joinedAt;

    // 3-argument constructor used in ConversationService
    public ConversationMember(Conversation conversation, User employee, MemberRole role) {
        this.conversation = conversation;
        this.employee     = employee;
        this.role         = role;
        this.joinedAt     = LocalDateTime.now();
    }
}