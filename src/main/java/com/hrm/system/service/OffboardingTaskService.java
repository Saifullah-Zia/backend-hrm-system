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

    // ─────────────────────────────────────────────────────
    // CREATE a custom offboarding task
    // ─────────────────────────────────────────────────────
    @Transactional
    public OffboardingTaskDto.Response createTask(OffboardingTaskDto.Request request) {

        Resignation resignation = resignationRepository.findById(request.getResignationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resignation not found: " + request.getResignationId()));

        // Get the EmployeeProfile from the resignation
        EmployeeProfile employeeProfile = resignation.getEmployeeProfile();

        User assignee = null;
        if (request.getAssignedToUserId() != null) {
            assignee = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found: " + request.getAssignedToUserId()));
        }

        OffboardingTask task = OffboardingTask.builder()
                .resignation(resignation)
                .employee(employeeProfile)          // EmployeeProfile, not User
                .taskName(request.getTaskName())
                .taskDescription(request.getTaskDescription())
                .category(request.getCategory())
                .dueDate(request.getDueDate())
                .assignedTo(assignee)
                .taskStatus(OffboardingTaskStatus.PENDING)
                .build();

        return mapToResponse(offboardingTaskRepository.save(task));
    }

    // ─────────────────────────────────────────────────────
    // GET all tasks for a resignation
    // ─────────────────────────────────────────────────────
    public List<OffboardingTaskDto.Response> getTasksByResignation(Long resignationId) {
        return offboardingTaskRepository.findByResignationId(resignationId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET tasks assigned to a user
    // ─────────────────────────────────────────────────────
    public List<OffboardingTaskDto.Response> getMyPendingTasks(Long userId) {
        return offboardingTaskRepository
                .findByAssignedToIdAndTaskStatus(userId, OffboardingTaskStatus.PENDING)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET overdue tasks
    // ─────────────────────────────────────────────────────
    public List<OffboardingTaskDto.Response> getOverdueTasks() {
        return offboardingTaskRepository.findOverdueTasks(LocalDate.now())
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // UPDATE task status (complete, skip, etc.)
    // ─────────────────────────────────────────────────────
    @Transactional
    public OffboardingTaskDto.Response updateTask(Long id, OffboardingTaskDto.UpdateRequest request) {
        OffboardingTask task = offboardingTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offboarding task not found: " + id));

        if (request.getTaskStatus()    != null) task.setTaskStatus(request.getTaskStatus());
        if (request.getCompletedDate() != null) task.setCompletedDate(request.getCompletedDate());
        if (request.getRemarks()       != null) task.setRemarks(request.getRemarks());

        // Auto-set completed date if marking as COMPLETED
        if (request.getTaskStatus() == OffboardingTaskStatus.COMPLETED
                && task.getCompletedDate() == null) {
            task.setCompletedDate(LocalDate.now());
        }

        return mapToResponse(offboardingTaskRepository.save(task));
    }

    // ─────────────────────────────────────────────────────
    // MAPPER — Entity → Response DTO
    // ─────────────────────────────────────────────────────
    private OffboardingTaskDto.Response mapToResponse(OffboardingTask t) {
        boolean overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now())
                && t.getTaskStatus() != OffboardingTaskStatus.COMPLETED
                && t.getTaskStatus() != OffboardingTaskStatus.SKIPPED;

        // Safely get employee name (null-safe)
        String employeeName = "";
        Long employeeId = null;
        if (t.getEmployee() != null) {
            employeeId = t.getEmployee().getId();
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