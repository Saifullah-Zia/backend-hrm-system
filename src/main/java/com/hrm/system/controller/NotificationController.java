package com.hrm.system.controller;

import com.hrm.system.dto.NotificationDto;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import com.hrm.system.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                String username = null;

                if (principal instanceof UserDetails userDetails) {
                    username = userDetails.getUsername();
                    System.out.println("🔍 Principal is UserDetails, username: " + username);
                } else if (principal instanceof String) {
                    username = (String) principal;
                    System.out.println("🔍 Principal is String, username: " + username);
                }

                if (username != null) {
                    // Try finding by email first
                    Optional<User> user = userRepository.findByEmail(username);
                    if (user.isEmpty()) {
                        System.out.println("🔍 Trying to find by name: " + username);
                        user = userRepository.findByName(username);
                    }

                    if (user.isPresent()) {
                        System.out.println("✅ Found user: " + user.get().getName() + " with ID: " + user.get().getId());
                        return user.get().getId();
                    } else {
                        System.err.println("❌ User not found in DB for username: " + username);
                    }
                } else {
                    System.err.println("❌ Principal is not UserDetails or String: " + principal);
                    System.err.println("Principal class: " + (principal != null ? principal.getClass() : "null"));
                }
            } else {
                System.err.println("❌ No authentication found in SecurityContext");
            }
        } catch (Exception e) {
            System.err.println("❌ Error getting current user: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            System.err.println("❌ Unauthorized access to /api/notifications");
            return ResponseEntity.status(401).build();
        }
        System.out.println("✅ Fetching notifications for userId: " + userId);
        return ResponseEntity.ok(notificationService.getNotificationsByUserId(userId));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            System.err.println("❌ Unauthorized access to /api/notifications/unread/count");
            return ResponseEntity.status(401).build();
        }
        Long count = notificationService.getUnreadCount(userId);
        System.out.println("✅ Unread count for userId " + userId + ": " + count);
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        notificationService.markAsRead(userId, id);
        System.out.println("✅ Marked notification " + id + " as read for userId: " + userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read/all")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        notificationService.markAllAsRead(userId);
        System.out.println("✅ Marked all notifications as read for userId: " + userId);
        return ResponseEntity.ok().build();
    }
}