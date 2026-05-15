package com.hrm.system.service;

import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProbationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;


    @Transactional
    public void startProbation(User user) {
        user.setProbationStartDate(LocalDate.now());
        user.setProbationEndDate(LocalDate.now().plusMonths(3));
        user.setProbationStatus(ProbationStatus.ON_PROBATION);
        user.setProbationNotificationSent(false);
        userRepository.save(user);
    }

    //Runs every day at midnight to check probation completions
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

    //HR confirms probation employee becomes permanent
    @Transactional
    public String confirmProbation(Long userId, Long confirmedByAdminId) {  // ← parameter name here
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found on this ID " + userId));

        if (user.getProbationStatus() == ProbationStatus.CONFIRMED) {
            return user.getName() + " is already confirmed as permanent staff.";
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
                confirmedByAdminId,   // ← must match parameter name exactly
                user.getId()
        );

        return user.getName() + " has been confirmed as permanent staff.";
    }

    //Check if user is on probation
    public boolean isOnProbation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getProbationStatus() == ProbationStatus.ON_PROBATION
                || user.getProbationStatus() == ProbationStatus.COMPLETED;
    }
}
