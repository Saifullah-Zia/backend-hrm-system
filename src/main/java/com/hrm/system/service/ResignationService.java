package com.hrm.system.service;

import com.hrm.system.dto.ResignationDto;
import com.hrm.system.enumm.OffboardingTaskCategory;
import com.hrm.system.enumm.OffboardingTaskStatus;
import com.hrm.system.enumm.ResignationStatus;
import com.hrm.system.exception.BadRequestException;
import com.hrm.system.exception.ResourceNotFoundException;
import com.hrm.system.model.*;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.OffboardingTaskRepository;
import com.hrm.system.repository.ResignationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final NotificationService notificationService;

    private static final String NOTIF_TYPE = "RESIGNATION";

    // ─────────────────────────────────────────────────────
    // SUBMIT a resignation
    // ─────────────────────────────────────────────────────
    @Transactional
    public ResignationDto.Response submitResignation(ResignationDto.Request request) {

        boolean alreadyExists = resignationRepository.existsByEmployeeProfile_IdAndStatusIn(
                request.getEmployeeId(),
                List.of(ResignationStatus.PENDING, ResignationStatus.APPROVED));

        if (alreadyExists) {
            throw new BadRequestException("Employee already has an active resignation request");
        }

        EmployeeProfile employee = employeeProfileRepository
                .findByIdWithUser(request.getEmployeeId())
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

        Resignation saved = resignationRepository.save(resignation);

        String employeeName   = employee.getFirstName() + " " + employee.getLastName();
        Long   employeeUserId = getUserId(employee);

        if (employeeUserId != null) {
            notificationService.createNotification(
                    employeeUserId,
                    "Your resignation has been submitted and is pending HR review. Last working day: "
                            + saved.getLastWorkingDay() + ".",
                    NOTIF_TYPE,
                    employeeUserId,
                    saved.getId()
            );
        }

        notifyAllHr(
                "New resignation submitted by " + employeeName
                        + ". Last working day: " + saved.getLastWorkingDay() + ".",
                employeeUserId,
                saved.getId()
        );

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────
    // GET all resignations (HR view)
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ResignationDto.Response> getAllResignations() {
        return resignationRepository.findAll()
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET by status
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ResignationDto.Response> getByStatus(ResignationStatus status) {
        return resignationRepository.findByStatus(status)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET by employee
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ResignationDto.Response> getByEmployee(Long employeeId) {
        return resignationRepository.findByEmployeeProfile_Id(employeeId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET single resignation
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
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
            throw new BadRequestException("Only PENDING resignations can be approved or rejected");
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

        if (request.getStatus() == ResignationStatus.APPROVED) {
            generateDefaultOffboardingTasks(resignation);
        }

        Resignation saved = resignationRepository.save(resignation);

        EmployeeProfile emp = employeeProfileRepository
                .findByIdWithUser(saved.getEmployeeProfile().getId())
                .orElse(saved.getEmployeeProfile());

        Long   empUserId = getUserId(emp);
        String empName   = emp.getFirstName() + " " + emp.getLastName();

        if (empUserId != null) {
            if (request.getStatus() == ResignationStatus.APPROVED) {
                notificationService.createNotification(
                        empUserId,
                        "Your resignation has been approved by HR. Your last working day is "
                                + saved.getLastWorkingDay()
                                + ". Please check your offboarding tasks.",
                        NOTIF_TYPE,
                        approvedByUserId,
                        saved.getId()
                );
            } else if (request.getStatus() == ResignationStatus.REJECTED) {
                notificationService.createNotification(
                        empUserId,
                        "Your resignation request has been rejected by HR"
                                + (request.getHrComments() != null
                                ? ". Reason: " + request.getHrComments()
                                : "") + ".",
                        NOTIF_TYPE,
                        approvedByUserId,
                        saved.getId()
                );
            }
        }

        return mapToResponse(saved);
    }

    // WITHDRAW a resignation (by employee)

    @Transactional
    public ResignationDto.Response withdrawResignation(Long id, String reason) {
        Resignation resignation = findResignationById(id);

        if (resignation.getStatus() == ResignationStatus.COMPLETED) {
            throw new BadRequestException("Cannot withdraw a completed resignation");
        }

        resignation.setStatus(ResignationStatus.WITHDRAWN);
        resignation.setHrComments("Withdrawn by employee. Reason: " + reason);

        Resignation saved = resignationRepository.save(resignation);

        EmployeeProfile emp = employeeProfileRepository
                .findByIdWithUser(saved.getEmployeeProfile().getId())
                .orElse(saved.getEmployeeProfile());

        Long   empUserId = getUserId(emp);
        String empName   = emp.getFirstName() + " " + emp.getLastName();

        if (empUserId != null) {
            notificationService.createNotification(
                    empUserId,
                    "Your resignation has been successfully withdrawn.",
                    NOTIF_TYPE,
                    empUserId,
                    saved.getId()
            );
        }

        notifyAllHr(
                empName + " has withdrawn their resignation.",
                empUserId,
                saved.getId()
        );

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────
    // COMPLETE offboarding
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

        Resignation saved = resignationRepository.save(resignation);

        EmployeeProfile emp = employeeProfileRepository
                .findByIdWithUser(saved.getEmployeeProfile().getId())
                .orElse(saved.getEmployeeProfile());

        Long   empUserId = getUserId(emp);
        String empName   = emp.getFirstName() + " " + emp.getLastName();

        if (empUserId != null) {
            notificationService.createNotification(
                    empUserId,
                    "Your offboarding process has been completed. "
                            + "Thank you for your service. We wish you all the best!",
                    NOTIF_TYPE,
                    empUserId,
                    saved.getId()
            );
        }

        notifyAllHr(
                "Offboarding for " + empName + " has been completed successfully.",
                empUserId,
                saved.getId()
        );

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ResignationDto.Response> getPaged(ResignationStatus status, Pageable pageable) {
        return resignationRepository
                .findAllPaged(status, pageable)
                .map(this::mapToResponse);
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


    // HELPER — safely get User ID from EmployeeProfile

    private Long getUserId(EmployeeProfile emp) {
        return (emp != null && emp.getUser() != null) ? emp.getUser().getId() : null;
    }

   
    // HELPER — notify all HR/ADMIN users

    private void notifyAllHr(String message, Long triggeredByUserId, Long referenceId) {
        userRepository.findByRole(Role.ADMIN)
                .forEach(hrUser -> notificationService.createNotification(
                        hrUser.getId(),
                        message,
                        NOTIF_TYPE,
                        triggeredByUserId,
                        referenceId
                ));
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