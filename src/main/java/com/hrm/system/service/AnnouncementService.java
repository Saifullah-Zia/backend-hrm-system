package com.hrm.system.service;

import com.hrm.system.dto.AnnouncementDto;
import com.hrm.system.dto.AuditLogDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.Announcement;
import com.hrm.system.model.User;
import com.hrm.system.repository.AnnouncementRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AuditLogService auditLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    // ── Convert Entity → DTO ──────────────────────────────────────────────────
    private AnnouncementDto toDto(Announcement a) {
        AnnouncementDto dto = new AnnouncementDto();
        dto.setId(a.getId());
        dto.setTitle(a.getTitle());
        dto.setContent(a.getContent());
        dto.setActive(a.isActive());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        if (a.getCreatedBy() != null) {
            userRepository.findById(a.getCreatedBy())
                    .ifPresent(u -> dto.setCreatedBy(u.getName()));
        }
        if (a.getUpdatedBy() != null) {
            userRepository.findById(a.getUpdatedBy())
                    .ifPresent(u -> dto.setUpdatedBy(u.getName()));
        }
        return dto;
    }

    // Queries
    public List<AnnouncementDto> getAllAnnouncement() {
        return announcementRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<AnnouncementDto> getActive() {
        return announcementRepository.findByActiveTrue()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<AnnouncementDto> getPaged(Pageable pageable) {
        return announcementRepository.findAll(pageable).map(this::toDto);
    }

    // Create
    public AnnouncementDto create(AnnouncementDto dto) {
        Announcement a = new Announcement();
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setActive(dto.isActive());

        AnnouncementDto saved = toDto(announcementRepository.save(a));

        // Send email notifications to all employees
        List<User> employees = userRepository.findAll();
        for (User employee : employees) {
            try {
                emailService.sendAnnouncementNotification(
                        employee.getEmail(),
                        employee.getName(),
                        saved.getTitle(),
                        saved.getContent()
                );
            } catch (Exception e) {
                log.warn("Failed to send announcement email to {}: {}", employee.getEmail(), e.getMessage());
            }
        }

        // ✅ Manual audit log — guaranteed to fire regardless of AOP
        logAudit(AuditAction.CREATE, saved.getId(), "Created Announcement: " + saved.getTitle());

        return saved;
    }

    // ── Update ────────────────────────────────────────────────────────────────
    public AnnouncementDto update(Long id, AnnouncementDto dto) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setActive(dto.isActive());

        AnnouncementDto saved = toDto(announcementRepository.save(a));

        // ✅ Manual audit log — guaranteed to fire regardless of AOP
        logAudit(AuditAction.UPDATE, saved.getId(), "Updated Announcement: " + saved.getTitle());

        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    public void delete(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
        String title = a.getTitle();
        announcementRepository.deleteById(id);

        // ✅ Manual audit log for delete too
        logAudit(AuditAction.DELETE, id, "Deleted Announcement: " + title);
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
                    .entityName("Announcement")
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