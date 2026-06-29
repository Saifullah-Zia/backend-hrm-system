package com.hrm.system.repository;


import com.hrm.system.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationMember cm1 ON cm1.conversation = c AND cm1.employee.id = :userId
        JOIN ConversationMember cm2 ON cm2.conversation = c AND cm2.employee.id = :targetId
        WHERE c.type = 'PRIVATE'
    """)
    Optional<Conversation> findPrivateBetween(
            @Param("userId") Long userId,
            @Param("targetId") Long targetId
    );

    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationMember cm ON cm.conversation = c
        WHERE cm.employee.id = :userId
        ORDER BY c.createdAt DESC
    """)
    List<Conversation> findAllByMember(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Conversation c SET c.createdBy = null WHERE c.createdBy.id = :userId")
    void nullifyCreatedBy(@Param("userId") Long userId);
}