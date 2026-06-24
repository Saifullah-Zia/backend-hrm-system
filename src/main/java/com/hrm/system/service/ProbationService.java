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

    @Autowired
    private EmailService emailService;

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
    public void startProbation(User user, LocalDate joiningDate) { // ✅ Add joiningDate parameter
        // ✅ Use joiningDate if provided, otherwise fallback to today
        LocalDate startDate = (joiningDate != null) ? joiningDate : LocalDate.now();

        user.setProbationStartDate(startDate);
        user.setProbationEndDate(startDate.plusMonths(3)); // Adds 3 months to joining date
        user.setProbationStatus(ProbationStatus.ON_PROBATION);
        user.setProbationNotificationSent(false);
        userRepository.save(user);
    }


    // SCHEDULED — runs every midnight to check completions

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

                String emailSubject = "Action Required: Probation Completed for " + user.getName();

                for (User admin : admins) {
                    // 1. Dashboard Bell Notification (You already had this)
                    notificationService.createNotification(
                            admin.getId(),
                            message,
                            "PROBATION",
                            user.getId(),
                            user.getId()
                    );

                    // 2. ✅ Send the Email to the Admin
                    // (Adjust the method name if your EmailService uses sendEmail instead of sendSimpleMessage)
                    emailService.sendSimpleMessage(
                            admin.getEmail(),
                            emailSubject,
                            message
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


    // UPDATE probation dates when HR edits the profile

    @Transactional
    public void updateProbation(User user, LocalDate newJoiningDate) {
        if (newJoiningDate == null || user.getProbationStatus() == ProbationStatus.CONFIRMED) {
            return;
        }

        user.setProbationStartDate(newJoiningDate);
        LocalDate newEndDate = newJoiningDate.plusMonths(3); // Assuming 3 months probation
        user.setProbationEndDate(newEndDate);

        // If the new end date is in the past, instantly change status to COMPLETED
        if (!LocalDate.now().isBefore(newEndDate)) {
            user.setProbationStatus(ProbationStatus.COMPLETED);
        } else {
            user.setProbationStatus(ProbationStatus.ON_PROBATION);
            user.setProbationNotificationSent(false); // Reset notification so it gets sent at midnight when it completes
        }

        userRepository.save(user);
    }


    // CHECK if user is on probation

    public boolean isOnProbation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getProbationStatus() == ProbationStatus.ON_PROBATION
                || user.getProbationStatus() == ProbationStatus.COMPLETED;
    }
}


