package com.hrm.system.service;

import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.Notice;
import com.hrm.system.model.User;
import com.hrm.system.repository.NoticeRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final AuditLogService auditLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    // ── Convert Entity → DTO ──────────────────────────────────────────────────
    private com.hrm.system.dto.NoticeDto toDto(Notice n) {
        com.hrm.system.dto.NoticeDto dto = new com.hrm.system.dto.NoticeDto();
        dto.setId(n.getId());
        dto.setUserId(n.getUserId());
        dto.setNoticeType(n.getNoticeType().name());
        dto.setTitle(n.getTitle());
        dto.setDescription(n.getDescription());
        dto.setEffectiveDate(n.getEffectiveDate());
        dto.setAttachmentUrl(n.getAttachmentUrl());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setCreatedBy(n.getCreatedBy());
        
        // Get employee name
        userRepository.findById(n.getUserId()).ifPresent(user -> dto.setEmployeeName(user.getName()));
        
        // Get admin name who created the notice
        if (n.getCreatedBy() != null) {
            userRepository.findById(n.getCreatedBy()).ifPresent(user -> dto.setCreatedByName(user.getName()));
        }
        
        return dto;
    }

    // ── Send Notice ───────────────────────────────────────────────────────────
    @Transactional
    public com.hrm.system.dto.NoticeDto sendNotice(com.hrm.system.dto.NoticeDto dto) {
        // Convert userId to Long if it's a String, Integer, or Long
        Long userIdLong;
        if (dto.getUserId() instanceof String) {
            userIdLong = Long.parseLong((String) dto.getUserId());
        } else if (dto.getUserId() instanceof Long) {
            userIdLong = (Long) dto.getUserId();
        } else if (dto.getUserId() instanceof Integer) {
            userIdLong = ((Integer) dto.getUserId()).longValue();
        } else {
            throw new RuntimeException("Invalid userId type: " + dto.getUserId());
        }

        User employee = userRepository.findById(userIdLong)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + userIdLong));

        Notice notice = new Notice();
        notice.setUserId(userIdLong);
        notice.setNoticeType(Notice.NoticeType.valueOf(dto.getNoticeType().toUpperCase()));
        notice.setTitle(dto.getTitle());
        notice.setDescription(dto.getDescription());
        notice.setEffectiveDate(dto.getEffectiveDate());
        notice.setAttachmentUrl(dto.getAttachmentUrl());

        Notice saved = noticeRepository.save(notice);
        com.hrm.system.dto.NoticeDto savedDto = toDto(saved);

        // Send email notification to employee using simple message
        try {
            String emailSubject = savedDto.getNoticeType() + " Notice: " + savedDto.getTitle();
            String emailBody = String.format(
                    "Dear %s,\n\nYou have received a %s notice:\n\nTitle: %s\nDescription: %s\nEffective Date: %s\n\nPlease login to the HRM system for more details.\n\nRegards,\nHR Department",
                    employee.getName(),
                    savedDto.getNoticeType(),
                    savedDto.getTitle(),
                    savedDto.getDescription(),
                    savedDto.getEffectiveDate() != null ? savedDto.getEffectiveDate().toString() : "N/A"
            );
            emailService.sendSimpleMessage(employee.getEmail(), emailSubject, emailBody);
        } catch (Exception e) {
            log.warn("Failed to send notice email to {}: {}", employee.getEmail(), e.getMessage());
        }

        // Create in-app notification for employee
        String message = String.format(
                "📢 You have received a %s notice: %s",
                savedDto.getNoticeType(),
                savedDto.getTitle()
        );
        notificationService.createNotification(
                employee.getId(),
                message,
                "NOTICE_RECEIVED",
                savedDto.getCreatedBy(),
                saved.getId()
        );

        // Audit log
        logAudit(AuditAction.CREATE, saved.getId(), "Sent " + savedDto.getNoticeType() + " notice to " + employee.getName());

        return savedDto;
    }

    // ── Get Notices by User ─────────────────────────────────────────────────────
    public List<com.hrm.system.dto.NoticeDto> getNoticesByUserId(Long userId) {
        return noticeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Get All Notices ─────────────────────────────────────────────────────────
    public List<com.hrm.system.dto.NoticeDto> getAllNotices() {
        return noticeRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Audit Helper ──────────────────────────────────────────────────────────
    private void logAudit(AuditAction action, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                log.warn("Audit skipped for [{}] - userId is null", action);
                return;
            }
            auditLogService.log(AuditLogDto.LogRequest.builder()
                    .entityName("Notice")
                    .entityId(entityId)
                    .action(action)
                    .description(description)
                    .performedByUserId(userId)
                    .ipAddress(getClientIp())
                    .build());
            log.info("Audit logged: action={}, entityId={}, userId={}", action, entityId, userId);
        } catch (Exception e) {
            log.warn("Audit log failed for [{}]: {}", action, e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attr =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            Object userId = attr.getRequest().getAttribute("userId");
            if (userId != null) {
                return Long.parseLong(userId.toString());
            }
            log.warn("userId attribute not found in request");
            return null;
        } catch (Exception e) {
            log.warn("Could not get current userId: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp() {
        try {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                            .getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            return ip != null ? ip.split(",")[0] : request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
