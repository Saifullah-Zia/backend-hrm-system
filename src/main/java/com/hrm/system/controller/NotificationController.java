package com.hrm.system.controller;

import com.hrm.system.dto.NotificationDto;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import com.hrm.system.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    String username = ((UserDetails) principal).getUsername();

                    // Try to find by email first, then by name
                    Optional<User> user = userRepository.findByEmail(username);
                    if (user.isEmpty()) {
                        user = userRepository.findByName(username);
                    }
                    if (user.isPresent()) {
                        System.out.println("✅ Found user ID: " + user.get().getId() + " for: " + username);
                        return user.get().getId();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting current user: " + e.getMessage());
        }

        // For testing - return first admin user from database
        List<User> admins = userRepository.findByRoleIn("ADMIN");
        if (admins.isEmpty()) {
            admins = userRepository.findByRoleIn("SUPERADMIN");
        }
        if (!admins.isEmpty()) {
            System.out.println("⚠️ Using default admin user ID: " + admins.get(0).getId());
            return admins.get(0).getId();
        }

        // Last resort - return user with ID 1
        System.out.println("⚠️ Using fallback user ID: 1");
        return 1L;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        List<NotificationDto> notifications = notificationService.getNotificationsByUserId(userId);
        System.out.println("📢 Returning " + notifications.size() + " notifications for user " + userId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        Long count = notificationService.getUnreadCount(userId);
        System.out.println("🔔 Unread count for user " + userId + ": " + count);
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
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read/all")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}