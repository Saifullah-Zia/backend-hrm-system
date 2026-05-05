package com.hrm.system.service;

import com.hrm.system.dto.AttendanceDto;
import com.hrm.system.model.Attendance;
import com.hrm.system.model.User;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             UserRepository userRepository) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    public AttendanceDto save(AttendanceDto dto) {
        if (dto.getUserId() == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        Attendance attendance = mapToEntity(dto);
        return mapToDto(attendanceRepository.save(attendance));
    }

    public List<AttendanceDto> getAll() {
        return attendanceRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public AttendanceDto getById(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance not found with id: " + id));
        return mapToDto(attendance);
    }

    public void delete(Long id) {
        if (!attendanceRepository.existsById(id)) {
            throw new EntityNotFoundException("Attendance not found with id: " + id);
        }
        attendanceRepository.deleteById(id);
    }

    private Attendance mapToEntity(AttendanceDto dto) {
        Attendance a = new Attendance();
        a.setId(dto.getId());
        a.setDate(dto.getDate());
        a.setStatus(dto.getStatus());
        a.setCheckIn(dto.getCheckIn());
        a.setCheckOut(dto.getCheckOut());

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + dto.getUserId()));
        a.setUser(user);
        return a;
    }

    private AttendanceDto mapToDto(Attendance entity) {
        AttendanceDto dto = new AttendanceDto();
        dto.setId(entity.getId());
        dto.setDate(entity.getDate());
        dto.setStatus(entity.getStatus());
        dto.setCheckIn(entity.getCheckIn());
        dto.setCheckOut(entity.getCheckOut());
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
        }
        return dto;
    }
}