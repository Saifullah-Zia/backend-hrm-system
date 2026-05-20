package com.hrm.system.service;

import com.hrm.system.dto.OffboardingTaskDto;
import com.hrm.system.enumm.OffboardingTaskStatus;
import com.hrm.system.exception.ResourceNotFoundException;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.OffboardingTask;
import com.hrm.system.model.Resignation;
import com.hrm.system.model.User;
import com.hrm.system.repository.OffboardingTaskRepository;
import com.hrm.system.repository.ResignationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OffboardingTaskService {

    private final OffboardingTaskRepository offboardingTaskRepository;
    private final ResignationRepository resignationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final String NOTIF_TYPE = "OFFBOARDING_TASK";

    // ─────────────────────────────────────────────────────
    // CREATE a custom offboarding task
    // ─────────────────────────────────────────────────────
    @Transactional
    public OffboardingTaskDto.Response createTask(OffboardingTaskDto.Request request) {

        Resignation resignation = resignationRepository.findById(request.getResignationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resignation not found: " + request.getResignationId()));

        EmployeeProfile employeeProfile = resignation.getEmployeeProfile();

        User assignee = null;
        if (request.getAssignedToUserId() != null) {
            assignee = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found: " + request.getAssignedToUserId()));
        }

        OffboardingTask task = OffboardingTask.builder()
                .resignation(resignation)
                .employee(employeeProfile)
                .taskName(request.getTaskName())
                .taskDescription(request.getTaskDescription())
                .category(request.getCategory())
                .dueDate(request.getDueDate())
                .assignedTo(assignee)
                .taskStatus(OffboardingTaskStatus.PENDING)
                .build();

        OffboardingTask saved = offboardingTaskRepository.save(task);

        if (assignee != null) {
            String employeeName = employeeProfile.getFirstName() + " " + employeeProfile.getLastName();
            notificationService.createNotification(
                    assignee.getId(),
                    "You have been assigned an offboarding task '" + saved.getTaskName()
                            + "' for employee " + employeeName
                            + (saved.getDueDate() != null ? ". Due: " + saved.getDueDate() : "") + ".",
                    NOTIF_TYPE,
                    employeeProfile.getUser().getId(),
                    saved.getId()
            );
        }

        notificationService.createNotification(
                employeeProfile.getUser().getId(),
                "An offboarding task '" + saved.getTaskName() + "' has been created for your exit process"
                        + (saved.getDueDate() != null ? " (due: " + saved.getDueDate() + ")" : "") + ".",
                NOTIF_TYPE,
                employeeProfile.getUser().getId(),
                saved.getId()
        );

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────
    // GET all tasks for a resignation
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OffboardingTaskDto.Response> getTasksByResignation(Long resignationId) {
        return offboardingTaskRepository.findByResignationId(resignationId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET tasks assigned to a user
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OffboardingTaskDto.Response> getMyPendingTasks(Long userId) {
        return offboardingTaskRepository
                .findByAssignedToIdAndTaskStatus(userId, OffboardingTaskStatus.PENDING)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET overdue tasks
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OffboardingTaskDto.Response> getOverdueTasks() {
        return offboardingTaskRepository.findOverdueTasks(LocalDate.now())
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // UPDATE task status
    // ─────────────────────────────────────────────────────
    @Transactional
    public OffboardingTaskDto.Response updateTask(Long id, OffboardingTaskDto.UpdateRequest request) {
        OffboardingTask task = offboardingTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offboarding task not found: " + id));

        OffboardingTaskStatus previousStatus = task.getTaskStatus();

        if (request.getTaskStatus()    != null) task.setTaskStatus(request.getTaskStatus());
        if (request.getCompletedDate() != null) task.setCompletedDate(request.getCompletedDate());
        if (request.getRemarks()       != null) task.setRemarks(request.getRemarks());

        if (request.getTaskStatus() == OffboardingTaskStatus.COMPLETED
                && task.getCompletedDate() == null) {
            task.setCompletedDate(LocalDate.now());
        }

        OffboardingTask updated = offboardingTaskRepository.save(task);

        if (request.getTaskStatus() != null && request.getTaskStatus() != previousStatus) {
            sendStatusChangeNotifications(updated, previousStatus);
        }

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────────────────
    // PRIVATE — send the right notifications on status change
    // ─────────────────────────────────────────────────────
    private void sendStatusChangeNotifications(OffboardingTask task, OffboardingTaskStatus previousStatus) {

        EmployeeProfile employee       = task.getEmployee();
        User            assignee       = task.getAssignedTo();
        Long            employeeUserId = (employee != null && employee.getUser() != null)
                ? employee.getUser().getId() : null;
        Long            actorId        = assignee != null ? assignee.getId() : employeeUserId;

        switch (task.getTaskStatus()) {

            case COMPLETED -> {
                String employeeName = employee != null
                        ? employee.getFirstName() + " " + employee.getLastName() : "Employee";

                if (employeeUserId != null) {
                    notificationService.createNotification(
                            employeeUserId,
                            "Your offboarding task '" + task.getTaskName() + "' has been marked as completed.",
                            NOTIF_TYPE,
                            actorId,
                            task.getId()
                    );
                }

                if (assignee != null && !assignee.getId().equals(employeeUserId)) {
                    notificationService.createNotification(
                            assignee.getId(),
                            "Offboarding task '" + task.getTaskName() + "' for " + employeeName
                                    + " has been marked as completed.",
                            NOTIF_TYPE,
                            actorId,
                            task.getId()
                    );
                }
            }

            case SKIPPED -> {
                if (assignee != null) {
                    notificationService.createNotification(
                            assignee.getId(),
                            "Offboarding task '" + task.getTaskName() + "' has been skipped"
                                    + (task.getRemarks() != null ? ": " + task.getRemarks() : "") + ".",
                            NOTIF_TYPE,
                            actorId,
                            task.getId()
                    );
                }
                if (employeeUserId != null) {
                    notificationService.createNotification(
                            employeeUserId,
                            "Your offboarding task '" + task.getTaskName() + "' has been skipped.",
                            NOTIF_TYPE,
                            actorId,
                            task.getId()
                    );
                }
            }

            case IN_PROGRESS -> {
                if (employeeUserId != null) {
                    notificationService.createNotification(
                            employeeUserId,
                            "Your offboarding task '" + task.getTaskName() + "' is now in progress.",
                            NOTIF_TYPE,
                            actorId,
                            task.getId()
                    );
                }
            }

            default -> { }
        }
    }

    // ─────────────────────────────────────────────────────
    // MAPPER — Entity → Response DTO
    // ─────────────────────────────────────────────────────
    private OffboardingTaskDto.Response mapToResponse(OffboardingTask t) {
        boolean overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now())
                && t.getTaskStatus() != OffboardingTaskStatus.COMPLETED
                && t.getTaskStatus() != OffboardingTaskStatus.SKIPPED;

        String employeeName = "";
        Long   employeeId   = null;
        if (t.getEmployee() != null) {
            employeeId   = t.getEmployee().getId();
            employeeName = t.getEmployee().getFirstName() + " " + t.getEmployee().getLastName();
        }

        return OffboardingTaskDto.Response.builder()
                .id(t.getId())
                .resignationId(t.getResignation().getId())
                .employeeId(employeeId)
                .employeeName(employeeName)
                .taskName(t.getTaskName())
                .taskDescription(t.getTaskDescription())
                .category(t.getCategory())
                .taskStatus(t.getTaskStatus())
                .dueDate(t.getDueDate())
                .completedDate(t.getCompletedDate())
                .isOverdue(overdue)
                .assignedToName(t.getAssignedTo() != null
                        ? t.getAssignedTo().getName() : "Unassigned")
                .remarks(t.getRemarks())
                .createdAt(t.getCreatedAt())
                .build();
    }
}