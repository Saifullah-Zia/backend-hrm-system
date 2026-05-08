package com.hrm.system.service;

import com.hrm.system.dto.NotificationDto;
import com.hrm.system.model.Notification;
import com.hrm.system.repository.NotificationRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    public void createNotification(Long userId, String message, String type, Long createdBy, Long referenceId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setType(type);
        notification.setCreatedBy(createdBy);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);
        System.out.println("📢 Notification created for user " + userId + ": " + message);
    }

    public List<NotificationDto> getNotificationsByUserId(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, "UNREAD");
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        notificationRepository.markAsRead(userId, notificationId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationDto convertToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType());
        dto.setStatus(notification.getStatus());
        dto.setUserId(notification.getUserId());
        dto.setCreatedBy(notification.getCreatedBy());
        dto.setReferenceId(notification.getReferenceId());
        dto.setCreatedAt(notification.getCreatedAt());

        // Calculate time ago
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(notification.getCreatedAt(), now);
        long hours = ChronoUnit.HOURS.between(notification.getCreatedAt(), now);
        long days = ChronoUnit.DAYS.between(notification.getCreatedAt(), now);

        if (minutes < 1) dto.setTimeAgo("Just now");
        else if (minutes < 60) dto.setTimeAgo(minutes + "m ago");
        else if (hours < 24) dto.setTimeAgo(hours + "h ago");
        else dto.setTimeAgo(days + "d ago");

        // Get created by name
        userRepository.findById(notification.getCreatedBy()).ifPresent(user ->
                dto.setCreatedByName(user.getName())
        );

        return dto;
    }
}