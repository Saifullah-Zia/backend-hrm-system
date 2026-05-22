package com.hrm.system.model;

import com.hrm.system.enumm.ConversationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener.class)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // ← THIS was missing
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ConversationType type;

    private String name;

    private String avatarUrl;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}