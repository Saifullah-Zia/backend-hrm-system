package com.hrm.system.service;

import com.hrm.system.config.AppTimeZone;
import com.hrm.system.dto.AttendanceDto;
import com.hrm.system.model.Attendance;
import com.hrm.system.model.User;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository       userRepository;
    private final OfficeHoursService   officeHoursService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             OfficeHoursService officeHoursService) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository       = userRepository;
        this.officeHoursService   = officeHoursService;
    }

    // ── Admin: manual record creation ────────────────────────────────────────

    @Transactional
    public AttendanceDto save(AttendanceDto dto) {
        if (dto.getUserId() == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        Attendance attendance = new Attendance();
        attendance.setDate(dto.getDate());

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + dto.getUserId()));
        attendance.setUser(user);

        // Convert checkIn (assumed PKT from frontend) → store as PKT
        if (dto.getCheckIn() != null) {
            ZonedDateTime checkInPKT = dto.getCheckIn()
                    .atZone(AppTimeZone.PKT);
            attendance.setCheckIn(checkInPKT.toLocalDateTime());
            attendance.setDate(checkInPKT.toLocalDate());
            attendance.setStatus(officeHoursService.calculateStatus(checkInPKT.toLocalTime()));
        } else {
            attendance.setStatus(dto.getStatus() != null ? dto.getStatus() : "ABSENT");
        }

        // Convert checkOut (assumed PKT from frontend) → store as PKT
        if (dto.getCheckOut() != null) {
            ZonedDateTime checkOutPKT = dto.getCheckOut()
                    .atZone(AppTimeZone.PKT);
            attendance.setCheckOut(checkOutPKT.toLocalDateTime());
        }

        return mapToDto(attendanceRepository.save(attendance));
    }

    // ── Employee: secure check-in (server stamps time) ───────────────────────

    @Transactional
    public AttendanceDto checkIn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Current time in PKT — employee cannot manipulate this
        ZonedDateTime nowPKT  = ZonedDateTime.now(AppTimeZone.PKT);
        LocalDate     todayPKT = nowPKT.toLocalDate();

        // Prevent double check-in on same day
        boolean alreadyCheckedIn = attendanceRepository
                .findByUserIdAndDate(userId, todayPKT)
                .isPresent();
        if (alreadyCheckedIn) {
            throw new IllegalStateException("Already checked in today");
        }

        Attendance attendance = new Attendance();
        attendance.setUser(user);
        attendance.setDate(todayPKT);
        attendance.setCheckIn(nowPKT.toLocalDateTime());  // stored as PKT
        attendance.setStatus(officeHoursService.calculateStatus(nowPKT.toLocalTime()));

        return mapToDto(attendanceRepository.save(attendance));
    }

    // ── Employee: secure check-out (server stamps time) ──────────────────────

    @Transactional
    public AttendanceDto checkOut(Long userId) {
        ZonedDateTime nowPKT   = ZonedDateTime.now(ZoneId.of("Asia/Karachi"));
        LocalDate     todayPKT = nowPKT.toLocalDate();

        Attendance attendance = attendanceRepository
                .findByUserIdAndDate(userId, todayPKT)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No check-in found for today. Please check in first."));

        if (attendance.getCheckOut() != null) {
            throw new IllegalStateException("Already checked out today");
        }

        attendance.setCheckOut(nowPKT.toLocalDateTime());
        return mapToDto(attendanceRepository.save(attendance));
    }

    // ── Admin: full record edit ───────────────────────────────────────────────

    @Transactional
    public AttendanceDto adminUpdate(Long id, AttendanceDto dto, Authentication auth) {
        // Only ADMIN / SUPERADMIN can call this
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPERADMIN"));
        if (!isAdmin) {
            throw new SecurityException("Only admins can edit attendance records");
        }

        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance not found: " + id));

        if (dto.getDate()   != null) attendance.setDate(dto.getDate());
        if (dto.getStatus() != null) attendance.setStatus(dto.getStatus());

        if (dto.getCheckIn() != null) {
            ZonedDateTime checkInPKT = dto.getCheckIn()
                    .atZone(AppTimeZone.PKT);
            attendance.setCheckIn(checkInPKT.toLocalDateTime());
        }
        if (dto.getCheckOut() != null) {
            ZonedDateTime checkOutPKT = dto.getCheckOut()
                    .atZone(AppTimeZone.PKT);
            attendance.setCheckOut(checkOutPKT.toLocalDateTime());
        }

        return mapToDto(attendanceRepository.save(attendance));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<AttendanceDto> getAll() {
        return attendanceRepository.findAll()
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttendanceDto.PageResponse getAllPaginated(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Attendance> result = attendanceRepository.findAll(pageable);
        List<AttendanceDto> content = result.getContent().stream()
                .map(this::mapToDto).collect(Collectors.toList());
        return new AttendanceDto.PageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @Transactional(readOnly = true)
    public AttendanceDto.PageResponse getUserPaginated(Long userId, int page, int size,
                                                       String sortBy, String sortDir) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Attendance> result = attendanceRepository.findByUserId(userId, pageable);
        List<AttendanceDto> content = result.getContent().stream()
                .map(this::mapToDto).collect(Collectors.toList());
        return new AttendanceDto.PageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    public AttendanceDto getById(Long id) {
        return mapToDto(attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance not found: " + id)));
    }

    public void delete(Long id) {
        if (!attendanceRepository.existsById(id)) {
            throw new EntityNotFoundException("Attendance not found: " + id);
        }
        attendanceRepository.deleteById(id);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AttendanceDto mapToDto(Attendance entity) {
        AttendanceDto dto = new AttendanceDto();
        dto.setId(entity.getId());
        dto.setDate(entity.getDate());
        dto.setStatus(entity.getStatus());
        dto.setCheckIn(entity.getCheckIn());
        dto.setCheckOut(entity.getCheckOut());
        if (entity.getUser() != null) dto.setUserId(entity.getUser().getId());
        return dto;
    }
}