package com.hrm.system.service;
import com.hrm.system.dto.ResignationDto;
import com.hrm.system.enumm.OffboardingTaskCategory;
import com.hrm.system.enumm.OffboardingTaskStatus;
import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.exception.BadRequestException;
import com.hrm.system.exception.ResourceNotFoundException;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.OffboardingTask;
import com.hrm.system.model.Resignation;
import com.hrm.system.model.User;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.OffboardingTaskRepository;
import com.hrm.system.repository.ResignationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResignationService {

    private final ResignationRepository resignationRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final UserRepository userRepository;
    private final OffboardingTaskRepository offboardingTaskRepository;

    // ─────────────────────────────────────────────────────
    // SUBMIT a resignation
    // ─────────────────────────────────────────────────────
    @Transactional
    public ResignationDto.Response submitResignation(ResignationDto.Request request) {

        // Prevent duplicate active resignations
        // FIX 1: existsByEmployeeIdAndStatusIn → existsByEmployeeProfile_IdAndStatusIn
        boolean alreadyExists = resignationRepository.existsByEmployeeProfile_IdAndStatusIn(
                request.getEmployeeId(),
                List.of(ResignationStatus.PENDING, ResignationStatus.APPROVED));

        if (alreadyExists) {
            throw new BadRequestException(
                    "Employee already has an active resignation request");
        }

        EmployeeProfile employee = employeeProfileRepository
                .findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + request.getEmployeeId()));

        Resignation resignation = Resignation.builder()
                .employeeProfile(employee)
                .resignationDate(request.getResignationDate())
                .lastWorkingDay(request.getLastWorkingDay())
                .reason(request.getReason())
                .resignationType(request.getResignationType())
                .status(ResignationStatus.PENDING)
                .build();

        return mapToResponse(resignationRepository.save(resignation));
    }

    // ─────────────────────────────────────────────────────
    // GET all resignations (HR view)
    // ─────────────────────────────────────────────────────
    public List<ResignationDto.Response> getAllResignations() {
        return resignationRepository.findAll()
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET by status
    // ─────────────────────────────────────────────────────
    public List<ResignationDto.Response> getByStatus(ResignationStatus status) {
        return resignationRepository.findByStatus(status)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET by employee
    // ─────────────────────────────────────────────────────
    public List<ResignationDto.Response> getByEmployee(Long employeeId) {
        // FIX 2: findByEmployeeId → findByEmployeeProfile_Id
        return resignationRepository.findByEmployeeProfile_Id(employeeId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET single resignation
    // ─────────────────────────────────────────────────────
    public ResignationDto.Response getById(Long id) {
        return mapToResponse(findResignationById(id));
    }

    // ─────────────────────────────────────────────────────
    // APPROVE / REJECT by HR
    // ─────────────────────────────────────────────────────
    @Transactional
    public ResignationDto.Response processResignation(
            Long id, ResignationDto.ApprovalRequest request, Long approvedByUserId) {

        Resignation resignation = findResignationById(id);

        if (resignation.getStatus() != ResignationStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING resignations can be approved or rejected");
        }

        User approver = userRepository.findById(approvedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + approvedByUserId));

        resignation.setStatus(request.getStatus());
        resignation.setHrComments(request.getHrComments());
        resignation.setIsEligibleForRehire(request.getIsEligibleForRehire());
        resignation.setNoticePeriodEndDate(request.getNoticePeriodEndDate());
        resignation.setApprovedBy(approver);
        resignation.setApprovedAt(LocalDateTime.now());

        // Auto-generate default offboarding checklist on approval
        if (request.getStatus() == ResignationStatus.APPROVED) {
            generateDefaultOffboardingTasks(resignation);
        }

        return mapToResponse(resignationRepository.save(resignation));
    }

    // ─────────────────────────────────────────────────────
    // WITHDRAW a resignation (by employee)
    // ─────────────────────────────────────────────────────
    @Transactional
    public ResignationDto.Response withdrawResignation(Long id, String reason) {
        Resignation resignation = findResignationById(id);

        if (resignation.getStatus() == ResignationStatus.COMPLETED) {
            throw new BadRequestException("Cannot withdraw a completed resignation");
        }

        resignation.setStatus(ResignationStatus.WITHDRAWN);
        resignation.setHrComments("Withdrawn by employee. Reason: " + reason);

        return mapToResponse(resignationRepository.save(resignation));
    }

    // ─────────────────────────────────────────────────────
    // COMPLETE offboarding (mark as fully done)
    // ─────────────────────────────────────────────────────
    @Transactional
    public ResignationDto.Response completeOffboarding(Long id) {
        Resignation resignation = findResignationById(id);

        long pendingTasks = offboardingTaskRepository
                .countByResignationIdAndTaskStatus(id, OffboardingTaskStatus.PENDING);

        if (pendingTasks > 0) {
            throw new BadRequestException(
                    "Cannot complete offboarding — " + pendingTasks + " tasks still pending");
        }

        resignation.setStatus(ResignationStatus.COMPLETED);
        resignation.setIsNoticePeriodServed(true);

        return mapToResponse(resignationRepository.save(resignation));
    }

    // ─────────────────────────────────────────────────────
    // AUTO-GENERATE default offboarding checklist
    // ─────────────────────────────────────────────────────
    private void generateDefaultOffboardingTasks(Resignation resignation) {
        LocalDate lastDay = resignation.getLastWorkingDay();

        List<OffboardingTask> defaultTasks = List.of(
                buildTask(resignation, "Return Laptop & Accessories",
                        "Collect all IT equipment from employee",
                        OffboardingTaskCategory.IT, lastDay),

                buildTask(resignation, "Revoke System Access",
                        "Disable email, VPN, and all system accounts",
                        OffboardingTaskCategory.IT, lastDay),

                buildTask(resignation, "Conduct Exit Interview",
                        "Schedule and conduct formal exit interview",
                        OffboardingTaskCategory.HR, lastDay),

                buildTask(resignation, "Process Final Settlement",
                        "Calculate and process final salary, leave encashment",
                        OffboardingTaskCategory.FINANCE, lastDay),

                buildTask(resignation, "Collect ID Card & Access Badge",
                        "Retrieve office ID and any access cards",
                        OffboardingTaskCategory.ADMIN, lastDay),

                buildTask(resignation, "Knowledge Transfer",
                        "Ensure all pending work is documented and handed over",
                        OffboardingTaskCategory.MANAGER, lastDay),

                buildTask(resignation, "Issue Experience Letter",
                        "Prepare and issue relieving/experience letter",
                        OffboardingTaskCategory.HR, lastDay),

                buildTask(resignation, "Expense Claims Clearance",
                        "Settle all pending expense reimbursements",
                        OffboardingTaskCategory.FINANCE, lastDay),

                buildTask(resignation, "NDA & IP Reminder",
                        "Send NDA and IP agreement reminders to employee",
                        OffboardingTaskCategory.LEGAL, lastDay)
        );

        offboardingTaskRepository.saveAll(defaultTasks);
    }

    // ─────────────────────────────────────────────────────
    // HELPER — build a single OffboardingTask
    // ─────────────────────────────────────────────────────
    private OffboardingTask buildTask(Resignation resignation, String name,
                                      String desc, OffboardingTaskCategory category, LocalDate dueDate) {
        return OffboardingTask.builder()
                .resignation(resignation)
                .employee(resignation.getEmployeeProfile())
                .taskName(name)
                .taskDescription(desc)
                .category(category)
                .taskStatus(OffboardingTaskStatus.PENDING)
                .dueDate(dueDate)
                .build();
    }

    // ─────────────────────────────────────────────────────
    // HELPER — find or throw
    // ─────────────────────────────────────────────────────
    private Resignation findResignationById(Long id) {
        return resignationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resignation not found with id: " + id));
    }

    // ─────────────────────────────────────────────────────
    // MAPPER — Entity → Response DTO
    // ─────────────────────────────────────────────────────
    private ResignationDto.Response mapToResponse(Resignation r) {
        long total     = offboardingTaskRepository.countByResignationId(r.getId());
        long completed = offboardingTaskRepository
                .countByResignationIdAndTaskStatus(r.getId(), OffboardingTaskStatus.COMPLETED);

        EmployeeProfile emp = r.getEmployeeProfile();

        return ResignationDto.Response.builder()
                .id(r.getId())
                .employeeId(emp.getId())
                .employeeName(emp.getFirstName() + " " + emp.getLastName())
                .employeeDepartment(emp.getDepartment() != null
                        ? emp.getDepartment().getName() : null)
                .employeePosition(emp.getPosition() != null
                        ? emp.getPosition().getTitle() : null)
                .resignationDate(r.getResignationDate())
                .lastWorkingDay(r.getLastWorkingDay())
                .noticePeriodEndDate(r.getNoticePeriodEndDate())
                .reason(r.getReason())
                .resignationType(r.getResignationType())
                .status(r.getStatus())
                .hrComments(r.getHrComments())
                .managerComments(r.getManagerComments())
                .isNoticePeriodServed(r.getIsNoticePeriodServed())
                .isEligibleForRehire(r.getIsEligibleForRehire())
                .approvedByName(r.getApprovedBy() != null
                        ? r.getApprovedBy().getName() : null)
                .approvedAt(r.getApprovedAt())
                .createdAt(r.getCreatedAt())
                .totalTasks((int) total)
                .completedTasks((int) completed)
                .pendingTasks((int) (total - completed))
                .build();
    }
}