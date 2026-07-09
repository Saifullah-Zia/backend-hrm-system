package com.hrm.system.service;

import com.hrm.system.dto.EmployeeProfileDto;
import com.hrm.system.model.Department;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.Position;
import com.hrm.system.model.Resignation;
import com.hrm.system.model.User;
import com.hrm.system.repository.DepartmentRepository;
import com.hrm.system.repository.DocumentRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.OffboardingTaskRepository;
import com.hrm.system.repository.PositionRepository;
import com.hrm.system.repository.ResignationRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    private final EmployeeProfileRepository employeeProfileRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final ResignationRepository resignationRepository;
    private final OffboardingTaskRepository offboardingTaskRepository;
    private final DocumentRepository documentRepository;
    private final ProbationService probationService;
    private final LeavePolicyService leavePolicyService;


    // MAPPER  Entity DTO

    // MAPPER  Entity DTO

    private EmployeeProfileDto toDto(EmployeeProfile profile) {
        return toDto(profile, true); // Admin creating/updating should receive the full DTO they just submitted
    }

    private EmployeeProfileDto toDto(EmployeeProfile profile, boolean revealSalary) {
        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUser() != null ? profile.getUser().getId() : null);
        dto.setFirstName(profile.getFirstName());
        dto.setLastName(profile.getLastName());
        dto.setPhone(profile.getPhone());
        dto.setAddress(profile.getAddress());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setJoiningDate(profile.getJoiningDate());
        dto.setCnicNumber(profile.getCnicNumber());
        dto.setProfilePicture(profile.getProfilePicture());
        dto.setEmergencyContactName(profile.getEmergencyContactName());
        dto.setEmergencyContactPhone(profile.getEmergencyContactPhone());
        dto.setDepartmentId(profile.getDepartment() != null ? profile.getDepartment().getId() : null);
        dto.setPositionId(profile.getPosition() != null ? profile.getPosition().getId() : null);
        dto.setEmploymentStatus(profile.getEmploymentStatus());
        dto.setBiometricPersonId(profile.getBiometricPersonId());
        dto.setBasicSalary(revealSalary ? profile.getBasicSalary() : null);
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        dto.setCreatedBy(profile.getCreatedBy());
        dto.setUpdatedBy(profile.getUpdatedBy());
        return dto;
    }


    // MAPPER  DTO  Entity

    private EmployeeProfile toEntity(EmployeeProfileDto dto) {
        EmployeeProfile profile = new EmployeeProfile();

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found with ID: " + dto.getUserId()));
        profile.setUser(user);

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Department not found with ID: " + dto.getDepartmentId()));
            profile.setDepartment(dept);
        }

        if (dto.getPositionId() != null) {
            Position position = positionRepository.findById(dto.getPositionId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Position not found with ID: " + dto.getPositionId()));
            profile.setPosition(position);
        }

        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setPhone(dto.getPhone());
        profile.setAddress(dto.getAddress());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setJoiningDate(dto.getJoiningDate());
        profile.setCnicNumber(dto.getCnicNumber());
        profile.setProfilePicture(dto.getProfilePicture());
        profile.setEmergencyContactName(dto.getEmergencyContactName());
        profile.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        profile.setEmploymentStatus(dto.getEmploymentStatus());
        profile.setBiometricPersonId(dto.getBiometricPersonId());
        profile.setBasicSalary(dto.getBasicSalary());

        return profile;
    }

    // ─────────────────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<EmployeeProfileDto> getAll() {
        return getAll(false);
    }

    @Transactional(readOnly = true)
    public List<EmployeeProfileDto> getAll(boolean revealSalary) {
        return employeeProfileRepository.findAllWithUsers()
                .stream()
                .map(p -> toDto(p, revealSalary))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────
    // GET BY PROFILE ID
    // ─────────────────────────────────────────────────────
    public EmployeeProfileDto getById(Long id) {
        return getById(id, false);
    }

    public EmployeeProfileDto getById(Long id, boolean revealSalary) {
        EmployeeProfile profile = employeeProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee profile not found with ID: " + id));
        return toDto(profile, revealSalary);
    }

    // ─────────────────────────────────────────────────────
    // GET BY USER ID
    // ─────────────────────────────────────────────────────
    public EmployeeProfileDto getByUserId(Long userId) {
        return getByUserId(userId, false);
    }

    public EmployeeProfileDto getByUserId(Long userId, boolean revealSalary) {
        EmployeeProfile profile = employeeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found for user ID: " + userId));
        return toDto(profile, revealSalary);
    }

    // ─────────────────────────────────────────────────────
    // GET CURRENT LOGGED-IN USER'S PROFILE
    // ─────────────────────────────────────────────────────
    public EmployeeProfileDto getMe() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");

        String email = ((UserDetails) auth.getPrincipal()).getUsername();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        return getByUserId(user.getId(), true); // Employees fetching their own profile can always see their own salary
    }



    // CREATE also starts probation automatically

    @Transactional
    public EmployeeProfileDto create(EmployeeProfileDto dto) {
        if (employeeProfileRepository.findByUserId(dto.getUserId()).isPresent())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Profile already exists for user ID: " + dto.getUserId());

        if (dto.getFirstName() == null || dto.getFirstName().isBlank()
                || dto.getLastName() == null || dto.getLastName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "First name and last name are required.");
        }

        if (dto.getEmploymentStatus() == null) {
            dto.setEmploymentStatus(com.hrm.system.model.EmploymentStatus.ACTIVE);
        }

        EmployeeProfile profile = toEntity(dto);
        EmployeeProfile saved = employeeProfileRepository.save(profile);

        // Sync basicSalary to User separately to avoid Hibernate cascade recursion
        if (dto.getBasicSalary() != null && saved.getUser() != null) {
            User user = saved.getUser();
            user.setBasicSalary(dto.getBasicSalary());
            userRepository.save(user);
        }

        // ✅ MODIFIED: Pass the joiningDate to the probation service
        probationService.startProbation(saved.getUser(), saved.getJoiningDate());
        leavePolicyService.ensureEligibilityBalancesForUser(saved.getUser());

        return toDto(saved);
    }


    // ─────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────
    @Transactional
    public EmployeeProfileDto update(Long id, EmployeeProfileDto dto) {
        EmployeeProfile profile = employeeProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee profile not found with ID: " + id));

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Department not found with ID: " + dto.getDepartmentId()));
            profile.setDepartment(dept);
        }

        if (dto.getPositionId() != null) {
            Position position = positionRepository.findById(dto.getPositionId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Position not found with ID: " + dto.getPositionId()));
            profile.setPosition(position);
        }

        if (dto.getFirstName() != null) profile.setFirstName(dto.getFirstName());
        if (dto.getLastName()  != null) profile.setLastName(dto.getLastName());

        profile.setPhone(dto.getPhone());
        profile.setAddress(dto.getAddress());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setJoiningDate(dto.getJoiningDate());
        profile.setCnicNumber(dto.getCnicNumber());
        profile.setProfilePicture(dto.getProfilePicture());
        profile.setEmergencyContactName(dto.getEmergencyContactName());
        profile.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        profile.setEmploymentStatus(dto.getEmploymentStatus());
        profile.setBiometricPersonId(dto.getBiometricPersonId());
        profile.setBasicSalary(dto.getBasicSalary() != null ? dto.getBasicSalary() : profile.getBasicSalary());
        probationService.updateProbation(profile.getUser(), dto.getJoiningDate());

        EmployeeProfile saved = employeeProfileRepository.save(profile);

        // Sync basicSalary to User separately to avoid Hibernate cascade recursion
        if (dto.getBasicSalary() != null && saved.getUser() != null) {
            User user = saved.getUser();
            user.setBasicSalary(dto.getBasicSalary());
            userRepository.save(user);
        }

        leavePolicyService.ensureEligibilityBalancesForUser(saved.getUser());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeProfileDto> getPaged(String search, Long departmentId, Pageable pageable) {
        return getPaged(search, departmentId, pageable, false);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeProfileDto> getPaged(String search, Long departmentId, Pageable pageable, boolean revealSalary) {
        // Convert to lowercase and add wildcards in Java
        String searchParam = (search == null || search.trim().isEmpty())
                ? null
                : "%" + search.trim().toLowerCase() + "%";

        return employeeProfileRepository
                .findAllPaged(searchParam, departmentId, pageable)
                .map(p -> toDto(p, revealSalary));
    }

    // ─────────────────────────────────────────────────────
    // DELETE — cascades through resignations → offboarding tasks
    // ─────────────────────────────────────────────────────
    @Transactional
    public void delete(Long id) {
        EmployeeProfile profile = employeeProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee profile not found with ID: " + id));

        // 1. Resignations → offboarding tasks
        List<Resignation> resignations = resignationRepository.findByEmployeeProfile_Id(id);
        for (Resignation r : resignations) {
            offboardingTaskRepository.deleteByResignationId(r.getId());
        }
        resignationRepository.deleteByEmployeeProfile_Id(id);

        // 2. Documents (FK blocks delete if left in place)
        documentRepository.deleteByEmployeeProfile_Id(id);

        User user = profile.getUser();
        if (user != null) {
            // Break User ↔ profile link before delete (avoids Hibernate merge on deleted instance)
            user.setEmployeeProfile(null);
            user.setProbationStartDate(null);
            user.setProbationEndDate(null);
            user.setProbationStatus(null);
            user.setProbationNotificationSent(false);
            userRepository.saveAndFlush(user);
        }

        // 3. Profile row
        employeeProfileRepository.delete(profile);
    }
}