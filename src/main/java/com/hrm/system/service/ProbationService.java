package com.hrm.system.service;

import com.hrm.system.dto.ProbationDto;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.EmployeeProfileRepository;
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

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

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
    @Transactional
    public List<ProbationDto.Response> getOnProbation() {
        syncProbationFromProfiles(employeeProfileRepository.findAllWithUsers());
        updateCompletedProbationsInline();
        return userRepository.findByProbationStatus(ProbationStatus.ON_PROBATION)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET all users with COMPLETED probation (awaiting HR confirmation)
    // ─────────────────────────────────────────────────────
    @Transactional
    public List<ProbationDto.Response> getAwaitingConfirmation() {
        syncProbationFromProfiles(employeeProfileRepository.findAllWithUsers());
        updateCompletedProbationsInline();
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
    public void startProbation(User user, LocalDate joiningDate) {
        if (joiningDate == null) {
            // Probation is tied to joining date — wait until employee profile provides it.
            return;
        }
        applyProbationFromJoiningDate(user, joiningDate);
    }

    /**
     * Sets probation start/end from joining date. Used on profile create/update and data sync.
     */
    @Transactional
    public void applyProbationFromJoiningDate(User user, LocalDate joiningDate) {
        if (joiningDate == null || user == null) {
            return;
        }
        if (user.getProbationStatus() == ProbationStatus.CONFIRMED) {
            return;
        }

        user.setProbationStartDate(joiningDate);
        LocalDate endDate = joiningDate.plusMonths(3);
        user.setProbationEndDate(endDate);

        if (!LocalDate.now().isBefore(endDate)) {
            user.setProbationStatus(ProbationStatus.COMPLETED);
        } else {
            user.setProbationStatus(ProbationStatus.ON_PROBATION);
            user.setProbationNotificationSent(false);
        }

        userRepository.save(user);
    }

    /**
     * Re-sync probation dates from employee profile joining dates (fixes legacy account-created dates).
     */
    @Transactional
    public void syncProbationFromProfiles(List<EmployeeProfile> profiles) {
        for (EmployeeProfile profile : profiles) {
            if (profile.getUser() == null || profile.getJoiningDate() == null) {
                continue;
            }
            User user = profile.getUser();
            if (user.getProbationStatus() == ProbationStatus.CONFIRMED) {
                continue;
            }
            // Always align probation window with profile joining date (fixes legacy account-created dates).
            applyProbationFromJoiningDate(user, profile.getJoiningDate());
        }
    }

    /** Admin/manual trigger — re-sync every employee profile joining date into probation fields. */
    @Transactional
    public int syncAllProbationFromProfiles() {
        List<EmployeeProfile> profiles = employeeProfileRepository.findAllWithUsers();
        syncProbationFromProfiles(profiles);
        updateCompletedProbationsInline();
        return profiles.size();
    }

    // ─────────────────────────────────────────────────────
    // SELF-HEALING / INLINE UPDATE check for completed probations
    // ─────────────────────────────────────────────────────
    @Transactional
    public void updateCompletedProbationsInline() {
        List<User> users = userRepository.findByProbationStatus(ProbationStatus.ON_PROBATION);
        LocalDate today = LocalDate.now();

        for (User user : users) {
            if (user.getProbationEndDate() != null && !today.isBefore(user.getProbationEndDate())) {
                user.setProbationStatus(ProbationStatus.COMPLETED);
                userRepository.save(user);

                // Send notification only if not already sent
                if (user.getProbationNotificationSent() == null || !user.getProbationNotificationSent()) {
                    user.setProbationNotificationSent(true);
                    userRepository.save(user);

                    try {
                        List<User> admins = userRepository.findByRole(Role.ADMIN);
                        String message = String.format(
                                "🔔 Probation period completed for %s. Please review and confirm their employment status.",
                                user.getName()
                        );
                        String emailSubject = "Action Required: Probation Completed for " + user.getName();

                        for (User admin : admins) {
                            notificationService.createNotification(
                                    admin.getId(),
                                    message,
                                    "PROBATION",
                                    user.getId(),
                                    user.getId()
                            );
                            
                            emailService.sendSimpleMessage(
                                    admin.getEmail(),
                                    emailSubject,
                                    message
                            );
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to send probation completion notification for " + user.getName());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // SCHEDULED — runs every midnight to check completions
    // ─────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkProbationCompletions() {
        updateCompletedProbationsInline();
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
        applyProbationFromJoiningDate(user, newJoiningDate);
    }


    // CHECK if user is on probation

    public boolean isOnProbation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getProbationStatus() == ProbationStatus.ON_PROBATION
                || user.getProbationStatus() == ProbationStatus.COMPLETED;
    }
}


