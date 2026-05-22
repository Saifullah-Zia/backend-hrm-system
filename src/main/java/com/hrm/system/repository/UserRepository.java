package com.hrm.system.repository;

import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByName(String name);
    List<User> findByRole(Role role);
    List<User> findByProbationStatus(ProbationStatus probationStatus);
    List<User> findByProbationStatusAndProbationNotificationSent(
            ProbationStatus probationStatus, boolean notificationSent);

    // REMOVED findByUsername — User has no username field

    @Modifying
    @Transactional
    @Query("""
        UPDATE User u
        SET u.presenceStatus = :status,
            u.lastSeenAt = :lastSeen
        WHERE u.email = :email
    """)
    void updatePresenceStatus(
            @Param("email") String email,
            @Param("status") String status,
            @Param("lastSeen") LocalDateTime lastSeen
    );

    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    List<User> searchByNameOrEmail(@Param("query") String query);
}