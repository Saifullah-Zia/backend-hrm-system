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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;



@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    @Autowired
    private final EmployeeProfileRepository employeeProfileRepository;
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final DepartmentRepository departmentRepository;
    @Autowired
    private final PositionRepository positionRepository;

    // Entity DTO
    private EmployeeProfileDto toDto(EmployeeProfile profile) {
        EmployeeProfileDto dto = new EmployeeProfileDto();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUser() != null
                ? profile.getUser().getId() : null);
        dto.setPhone(profile.getPhone());
        dto.setAddress(profile.getAddress());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setJoiningDate(profile.getJoiningDate());
        dto.setCnicNumber(profile.getCnicNumber());
        dto.setProfilePicture(profile.getProfilePicture());
        dto.setEmergencyContactName(profile.getEmergencyContactName());
        dto.setEmergencyContactPhone(profile.getEmergencyContactPhone());
        dto.setDepartmentId(profile.getDepartment() != null
                ? profile.getDepartment().getId() : null);
        dto.setPositionId(profile.getPosition() != null
                ? profile.getPosition().getId() : null);
        dto.setEmploymentStatus(profile.getEmploymentStatus());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        dto.setCreatedBy(profile.getCreatedBy());
        dto.setUpdatedBy(profile.getUpdatedBy());
        return dto;
    }

    //  DTO Entity
    private EmployeeProfile toEntity(EmployeeProfileDto dto) {
        EmployeeProfile profile = new EmployeeProfile();

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        profile.setUser(user);

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            profile.setDepartment(dept);
        }

        if (dto.getPositionId() != null) {
            Position position = positionRepository.findById(dto.getPositionId())
                    .orElseThrow(() -> new RuntimeException("Position not found"));
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
                .orElseThrow(() -> new RuntimeException("Employee profile not found"));
        return toDto(profile);
    }

    public EmployeeProfileDto getByUserId(Long userId) {
        EmployeeProfile profile = employeeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for this user"));
        return toDto(profile);
    }

    public EmployeeProfileDto create(EmployeeProfileDto dto) {
        // check if profile already exists for this user
        if (employeeProfileRepository.findByUserId(dto.getUserId()).isPresent())
            throw new RuntimeException("Profile already exists for this user");
        EmployeeProfile profile = toEntity(dto);
        return toDto(employeeProfileRepository.save(profile));
    }

    public EmployeeProfileDto update(Long id, EmployeeProfileDto dto) {
        EmployeeProfile profile = employeeProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee profile not found"));

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            profile.setDepartment(dept);
        }

        if (dto.getPositionId() != null) {
            Position position = positionRepository.findById(dto.getPositionId())
                    .orElseThrow(() -> new RuntimeException("Position not found"));
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
            throw new RuntimeException("Employee profile not found");
        employeeProfileRepository.deleteById(id);
    }
}