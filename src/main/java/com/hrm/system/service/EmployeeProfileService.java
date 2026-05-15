// EmployeeProfileService.java
package com.hrm.system.service;

import com.hrm.system.dto.EmployeeProfileDto;
import com.hrm.system.model.Department;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.Position;
import com.hrm.system.model.User;
import com.hrm.system.repository.DepartmentRepository;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.PositionRepository;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    private final EmployeeProfileRepository employeeProfileRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    private EmployeeProfileDto toDto(EmployeeProfile profile) {
        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUser() != null ? profile.getUser().getId() : null);
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
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        dto.setCreatedBy(profile.getCreatedBy());
        dto.setUpdatedBy(profile.getUpdatedBy());
        return dto;
    }

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

        profile.setPhone(dto.getPhone());
        profile.setAddress(dto.getAddress());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setJoiningDate(dto.getJoiningDate());
        profile.setCnicNumber(dto.getCnicNumber());
        profile.setProfilePicture(dto.getProfilePicture());
        profile.setEmergencyContactName(dto.getEmergencyContactName());
        profile.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        profile.setEmploymentStatus(dto.getEmploymentStatus());

        return profile;
    }

    public List<EmployeeProfileDto> getAll() {
        return employeeProfileRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public EmployeeProfileDto getById(Long id) {
        EmployeeProfile profile = employeeProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee profile not found with ID: " + id));
        return toDto(profile);
    }

    public EmployeeProfileDto getByUserId(Long userId) {
        EmployeeProfile profile = employeeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found for user ID: " + userId));
        return toDto(profile);
    }

    public EmployeeProfileDto getMe() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");

        // auth.getPrincipal() is UserDetails (set in JwtFilter)
        String email = ((UserDetails) auth.getPrincipal()).getUsername();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        return getByUserId(user.getId());
    }

    public EmployeeProfileDto create(EmployeeProfileDto dto) {
        if (employeeProfileRepository.findByUserId(dto.getUserId()).isPresent())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Profile already exists for user ID: " + dto.getUserId());
        EmployeeProfile profile = toEntity(dto);
        return toDto(employeeProfileRepository.save(profile));
    }

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

        profile.setPhone(dto.getPhone());
        profile.setAddress(dto.getAddress());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setJoiningDate(dto.getJoiningDate());
        profile.setCnicNumber(dto.getCnicNumber());
        profile.setProfilePicture(dto.getProfilePicture());
        profile.setEmergencyContactName(dto.getEmergencyContactName());
        profile.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        profile.setEmploymentStatus(dto.getEmploymentStatus());

        return toDto(employeeProfileRepository.save(profile));
    }

    public void delete(Long id) {
        if (!employeeProfileRepository.existsById(id))
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Employee profile not found with ID: " + id);
        employeeProfileRepository.deleteById(id);
    }
}