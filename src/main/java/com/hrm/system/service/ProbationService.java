package com.hrm.system.service;

import com.hrm.system.dto.ProbationDto;
import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProbationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    // ─────────────────────────────────────────────────────
    // MAPPER — User → ProbationDto.Response
    // ─────────────────────────────────────────────────────
    private ProbationDto.Response mapToResponse(User user) {
        return ProbationDto.Response.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .probationStatus(user.getProbationStatus())
                .probationStartDate(user.getProbationStartDate())
                .probationEndDate(user.getProbationEndDate())
                .probationNotificationSent(user.getProbationNotificationSent())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────
    // GET all users ON_PROBATION
    // ─────────────────────────────────────────────────────
    public List<ProbationDto.Response> getOnProbation() {
        return userRepository.findByProbationStatus(ProbationStatus.ON_PROBATION)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET all users with COMPLETED probation (awaiting HR confirmation)
    // ─────────────────────────────────────────────────────
    public List<ProbationDto.Response> getAwaitingConfirmation() {
        return userRepository.findByProbationStatus(ProbationStatus.COMPLETED)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET all CONFIRMED permanent staff
    // ─────────────────────────────────────────────────────
    public List<ProbationDto.Response> getConfirmed() {
        return userRepository.findByProbationStatus(ProbationStatus.CONFIRMED)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // START probation when employee profile is created
    // ─────────────────────────────────────────────────────
    @Transactional
    public void startProbation(User user) {
        user.setProbationStartDate(LocalDate.now());
        user.setProbationEndDate(LocalDate.now().plusMonths(3));
        user.setProbationStatus(ProbationStatus.ON_PROBATION);
        user.setProbationNotificationSent(false);
        userRepository.save(user);
    }

    // ─────────────────────────────────────────────────────
    // SCHEDULED — runs every midnight to check completions
    // ─────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkProbationCompletions() {
        List<User> users = userRepository.findByProbationStatusAndProbationNotificationSent(
                ProbationStatus.ON_PROBATION, false);

        LocalDate today = LocalDate.now();

        for (User user : users) {
            if (user.getProbationEndDate() != null
                    && !today.isBefore(user.getProbationEndDate())) {

                user.setProbationStatus(ProbationStatus.COMPLETED);
                user.setProbationNotificationSent(true);
                userRepository.save(user);

                List<User> admins = userRepository.findByRole(Role.ADMIN);

                String message = String.format(
                        "🔔 Probation period completed for %s. Please review and confirm their employment status.",
                        user.getName()
                );

                for (User admin : admins) {
                    notificationService.createNotification(
                            admin.getId(),
                            message,
                            "PROBATION",
                            user.getId(),
                            user.getId()
                    );
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // CONFIRM probation — HR manually confirms permanent staff
    // ─────────────────────────────────────────────────────
    @Transactional
    public ProbationDto.Response confirmProbation(Long userId, Long confirmedByAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        if (user.getProbationStatus() == ProbationStatus.CONFIRMED) {
            throw new RuntimeException(user.getName() + " is already confirmed as permanent staff.");
        }

        if (user.getProbationStatus() == ProbationStatus.ON_PROBATION) {
            throw new RuntimeException(
                    user.getName() + " probation period has not ended yet. Ends on: "
                            + user.getProbationEndDate());
        }

        user.setProbationStatus(ProbationStatus.CONFIRMED);
        userRepository.save(user);

        notificationService.createNotification(
                user.getId(),
                "🎉 Congratulations! Your probation period has been completed and you are now confirmed as permanent staff.",
                "PROBATION",
                confirmedByAdminId,
                user.getId()
        );

        return mapToResponse(user);
    }

    // ─────────────────────────────────────────────────────
    // CHECK if user is on probation
    // ─────────────────────────────────────────────────────
    public boolean isOnProbation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getProbationStatus() == ProbationStatus.ON_PROBATION
                || user.getProbationStatus() == ProbationStatus.COMPLETED;
    }
}