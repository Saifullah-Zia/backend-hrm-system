package com.hrm.system.repository;

import com.hrm.system.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(
            UUID conversationId,
            Pageable pageable
    );

    Optional<ChatMessage> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE ChatMessage m
        SET m.isRead = true
        WHERE m.conversation.id = :conversationId
        AND m.sender.email != :email
        AND m.isRead = false
    """)
    void markAllReadInConversation(
            @Param("conversationId") UUID conversationId,
            @Param("email") String email         // email not username
    );

    @Query("""
        SELECT COUNT(m) FROM ChatMessage m
        WHERE m.conversation.id = :conversationId
        AND m.sender.email != :email
        AND m.isRead = false
    """)
    int countUnread(
            @Param("conversationId") UUID conversationId,
            @Param("email") String email         // email not username
    );
}