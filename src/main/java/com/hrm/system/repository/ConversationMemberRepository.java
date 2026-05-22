package com.hrm.system.repository;

import com.hrm.system.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    List<ConversationMember> findByConversationId(UUID conversationId);

    Optional<ConversationMember> findByConversationIdAndEmployeeEmail(
            UUID conversationId,
            String email
    );

    Optional<ConversationMember> findByConversationIdAndEmployeeId(
            UUID conversationId,
            Long employeeId    // Long not UUID
    );

    boolean existsByConversationIdAndEmployeeId(
            UUID conversationId,
            Long employeeId    // Long not UUID
    );

    void deleteByConversationIdAndEmployeeId(
            UUID conversationId,
            Long employeeId    // Long not UUID
    );
}