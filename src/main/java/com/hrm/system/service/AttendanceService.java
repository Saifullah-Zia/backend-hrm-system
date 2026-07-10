package com.hrm.system.service;

import com.hrm.system.config.AppTimeZone;
import com.hrm.system.dto.AttendanceDto;
import com.hrm.system.dto.AttendanceSummaryDto;
import com.hrm.system.dto.ManualAttendanceResultDto;
import com.hrm.system.model.*;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.AttendanceSummaryRepository;
import com.hrm.system.repository.LeaveRepository;
import com.hrm.system.repository.PayrollPeriodRepository;
import com.hrm.system.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hrm.system.service.OfficeHoursService;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository       userRepository;
    private final OfficeHoursService   officeHoursService;
    private final AttendanceSummaryRepository attendanceSummaryRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final LeaveRepository      leaveRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             OfficeHoursService officeHoursService,
                             AttendanceSummaryRepository attendanceSummaryRepository,
                             PayrollPeriodRepository payrollPeriodRepository,
                             LeaveRepository leaveRepository) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository       = userRepository;
        this.officeHoursService   = officeHoursService;
        this.attendanceSummaryRepository = attendanceSummaryRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.leaveRepository      = leaveRepository;
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
        ZonedDateTime nowPKT   = ZonedDateTime.now(AppTimeZone.PKT);
        System.out.println("checkOut called for user ID: " + userId + ", current PKT time: " + nowPKT);

        // Find the most recent attendance record with no check-out
        Attendance attendance = attendanceRepository
                .findFirstByUserIdAndCheckOutIsNullOrderByCheckInDesc(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No pending check-in found. Please check in first."));
        System.out.println("Found attendance record for user ID: " + userId + ", check-in time: " + attendance.getCheckIn() + ", date: " + attendance.getDate());

        if (attendance.getCheckOut() != null) {
            System.out.println("User ID: " + userId + " already checked out at: " + attendance.getCheckOut());
            throw new IllegalStateException("Already checked out");
        }

        attendance.setCheckOut(nowPKT.toLocalDateTime());
        System.out.println("Setting check-out time for user ID: " + userId + " to: " + nowPKT.toLocalDateTime());
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

    // ── Leave Integration Helpers ─────────────────────────────────────────────

    /**
     * Creates or updates the attendance record for an approved leave day.
     *
     * IMPORTANT: this method is called from LeaveService.approveLeave() for every
     * day in the leave range. It must NOT blindly skip when a record already exists,
     * because the nightly "mark absent" job may have already created an ABSENT
     * placeholder for that date before the leave got approved (e.g. leave approved
     * late, or the absent job ran while a request was still PENDING).
     *
     * Rule:
     *  - If the employee actually checked in (checkIn != null) → never touch it,
     *    a real check-in always wins.
     *  - Otherwise (no record, or a placeholder ABSENT/other record with no check-in)
     *    → set/overwrite the status with the leave status (ON_LEAVE / UNPAID_LEAVE).
     */
    @Transactional
    public void createOrUpdateAttendanceForLeave(Long userId, LocalDate date, String status) {
        Optional<Attendance> existing = attendanceRepository.findByUserIdAndDate(userId, date);

        if (existing.isPresent()) {
            Attendance attendance = existing.get();

            // Don't override a real check-in — employee actually showed up.
            if (attendance.getCheckIn() != null) {
                return;
            }

            // Safe to overwrite a no-check-in placeholder (e.g. stale ABSENT record)
            // with the correct leave status.
            attendance.setStatus(status);
            attendanceRepository.save(attendance);
            return;
        }

        // No record yet — create a fresh one for the leave day.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Attendance attendance = new Attendance();
        attendance.setUser(user);
        attendance.setDate(date);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);
    }

    @Transactional
    public void markAbsentIfNoRecord(Long userId, LocalDate date) {
        // Only mark absent if no attendance record exists
        Optional<Attendance> existing = attendanceRepository.findByUserIdAndDate(userId, date);
        if (existing.isPresent()) {
            // Don't override existing record (check-in or leave)
            return;
        }

        // Create absent record
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Attendance attendance = new Attendance();
        attendance.setUser(user);
        attendance.setDate(date);
        attendance.setStatus("ABSENT");
        attendanceRepository.save(attendance);
    }

    // ── Admin: Manual attendance marking for a date range (backup for scheduled job) ──

    /**
     * Backup for the nightly markAllAbsentForYesterday job. HR can trigger this
     * manually for a date range if the scheduled job didn't run, or to backfill
     * a period.
     *
     * Rules per employee per day (weekends skipped entirely):
     *  1. Real check-in exists → never touch it.
     *  2. Approved leave overlaps this date → create/overwrite with ON_LEAVE
     *     or UNPAID_LEAVE (matches the logic in createOrUpdateAttendanceForLeave).
     *  3. No leave, no record → create ABSENT.
     *  4. No leave, already ABSENT → no-op.
     */
    @Transactional
    public ManualAttendanceResultDto markAttendanceForDateRange(LocalDate startDate, LocalDate endDate, List<Long> userIds) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        List<User> targetUsers = (userIds != null && !userIds.isEmpty())
                ? userRepository.findAllById(userIds).stream()
                .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.SUPERADMIN)
                .collect(Collectors.toList())
                : userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.SUPERADMIN)
                .collect(Collectors.toList());

        if (targetUsers.isEmpty()) {
            return new ManualAttendanceResultDto(0, 0, 0, 0);
        }

        List<Long> resolvedUserIds = targetUsers.stream().map(User::getId).collect(Collectors.toList());

        // Bulk-fetch once — avoids N queries per employee per day
        List<Attendance> existingRecords = attendanceRepository
                .findByUserIdInAndDateBetween(resolvedUserIds, startDate, endDate);
        Map<String, Attendance> existingByKey = existingRecords.stream()
                .collect(Collectors.toMap(a -> a.getUser().getId() + "_" + a.getDate(), a -> a));

        List<Leave> approvedLeaves = leaveRepository.findByStatusAndDateRangeOverlapAndUserIdIn(
                LeaveStatus.APPROVED, startDate, endDate, resolvedUserIds);

        int created = 0, updatedToLeave = 0, skippedHandled = 0, skippedWeekend = 0;

        for (User user : targetUsers) {
            LocalDate date = startDate;
            while (!date.isAfter(endDate)) {
                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    skippedWeekend++;
                    date = date.plusDays(1);
                    continue;
                }

                Attendance existing = existingByKey.get(user.getId() + "_" + date);

                // Rule 1: real check-in always wins — never touch it
                if (existing != null && existing.getCheckIn() != null) {
                    skippedHandled++;
                    date = date.plusDays(1);
                    continue;
                }

                LocalDate finalDate = date;
                Optional<Leave> matchingLeave = approvedLeaves.stream()
                        .filter(l -> l.getUser().getId().equals(user.getId())
                                && !finalDate.isBefore(l.getStartDate())
                                && !finalDate.isAfter(l.getEndDate()))
                        .findFirst();

                if (matchingLeave.isPresent()) {
                    String status = matchingLeave.get().getType().equalsIgnoreCase("UNPAID")
                            ? "UNPAID_LEAVE" : "ON_LEAVE";
                    if (existing == null) {
                        Attendance a = new Attendance();
                        a.setUser(user);
                        a.setDate(date);
                        a.setStatus(status);
                        attendanceRepository.save(a);
                        created++;
                    } else if (!status.equals(existing.getStatus())) {
                        existing.setStatus(status);
                        attendanceRepository.save(existing);
                        updatedToLeave++;
                    } else {
                        skippedHandled++;
                    }
                } else {
                    if (existing == null) {
                        Attendance a = new Attendance();
                        a.setUser(user);
                        a.setDate(date);
                        a.setStatus("ABSENT");
                        attendanceRepository.save(a);
                        created++;
                    } else {
                        skippedHandled++; // already ABSENT, no leave — no-op
                    }
                }

                date = date.plusDays(1);
            }
        }

        return new ManualAttendanceResultDto(created, updatedToLeave, skippedHandled, skippedWeekend);
    }

    // ── Payroll Integration: Attendance Summary Generation ─────────────────────

    @Transactional
    public AttendanceSummaryDto generateAttendanceSummary(Long employeeId, Long payrollPeriodId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new EntityNotFoundException("Payroll period not found: " + payrollPeriodId));

        // Parse month and year to get date range
        String month = payrollPeriod.getMonth();
        Integer year = payrollPeriod.getYear();

        // Get first and last day of the month
        String monthStr = month;
        if (monthStr != null && monthStr.contains(" ")) {
            monthStr = monthStr.split(" ")[0];
        }
        YearMonth yearMonth = YearMonth.of(year, Month.valueOf(monthStr != null ? monthStr.trim().toUpperCase() : ""));
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Get attendance records for the period
        List<Attendance> attendances = attendanceRepository.findByUserIdAndDateBetween(employeeId, startDate, endDate);

        // Count by status
        int presentDays = (int) attendances.stream().filter(a -> "PRESENT".equals(a.getStatus())).count();
        int lateDays = (int) attendances.stream().filter(a -> "LATE".equals(a.getStatus())).count();
        int paidLeaveDays = (int) attendances.stream().filter(a -> "ON_LEAVE".equals(a.getStatus())).count();
        int unpaidLeaveDays = (int) attendances.stream().filter(a -> "UNPAID_LEAVE".equals(a.getStatus())).count();
        int absentDays = (int) attendances.stream().filter(a -> "ABSENT".equals(a.getStatus())).count();

        // Calculate working days (excluding weekends - Saturday, Sunday)
        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        // Check if summary already exists
        Optional<AttendanceSummary> existing = attendanceSummaryRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId);

        AttendanceSummary summary;
        if (existing.isPresent()) {
            summary = existing.get();
        } else {
            summary = new AttendanceSummary();
            summary.setEmployee(employee);
            summary.setPayrollPeriod(payrollPeriod);
        }

        summary.setPresentDays(presentDays);
        summary.setLateDays(lateDays);
        summary.setPaidLeaveDays(paidLeaveDays);
        summary.setUnpaidLeaveDays(unpaidLeaveDays);
        summary.setAbsentDays(absentDays);
        summary.setWorkingDays(workingDays);

        AttendanceSummary saved = attendanceSummaryRepository.save(summary);
        return mapToAttendanceSummaryDto(saved);
    }

    @Transactional
    public void generateBulkAttendanceSummaries(Long payrollPeriodId) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(payrollPeriodId)
                .orElseThrow(() -> new EntityNotFoundException("Payroll period not found: " + payrollPeriodId));

        // Get all employees (Role.EMPLOYEE only)
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole() == Role.EMPLOYEE)
                .collect(Collectors.toList());

        for (User employee : employees) {
            try {
                generateAttendanceSummary(employee.getId(), payrollPeriodId);
            } catch (Exception e) {
                System.err.println("Failed to generate attendance summary for employee: " + employee.getId());
                e.printStackTrace();
            }
        }
    }

    private AttendanceSummaryDto mapToAttendanceSummaryDto(AttendanceSummary summary) {
        AttendanceSummaryDto dto = new AttendanceSummaryDto();
        dto.setId(summary.getId());
        dto.setEmployeeId(summary.getEmployee().getId());
        dto.setEmployeeName(summary.getEmployee().getName());
        dto.setPayrollPeriodId(summary.getPayrollPeriod().getId());
        dto.setPresentDays(summary.getPresentDays());
        dto.setLateDays(summary.getLateDays());
        dto.setPaidLeaveDays(summary.getPaidLeaveDays());
        dto.setUnpaidLeaveDays(summary.getUnpaidLeaveDays());
        dto.setAbsentDays(summary.getAbsentDays());
        dto.setWorkingDays(summary.getWorkingDays());
        dto.setCreatedAt(summary.getCreatedAt());
        return dto;
    }

    // ── Scheduled Job: Mark Absences ─────────────────────────────────────────────

    // Explicit zone = "Asia/Karachi" so this fires at 2:30 AM PKT regardless of the
    // server/container's default timezone (Railway containers default to UTC, which
    // was previously causing this to fire at 2:30 AM UTC == 7:30 AM PKT).
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Karachi") // runs 2:30 AM PKT daily — 30 min after shift end (2 AM)
    @Transactional
    public void markAllAbsentForYesterday() {
        // Shift starts 5 PM and ends ~2 AM next day, so the "attendance date"
        // for a shift that started yesterday evening is still yesterday's date.
        LocalDate shiftDate = LocalDate.now(AppTimeZone.PKT).minusDays(1);

        // Exclude ADMIN/SUPERADMIN — they are not tracked for daily attendance.
        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN && u.getRole() != Role.SUPERADMIN)
                .collect(Collectors.toList());

        for (User user : allUsers) {
            try {
                markAbsentIfNoRecord(user.getId(), shiftDate);
            } catch (Exception e) {
                System.err.println("Failed to mark absent for user " + user.getId() + " on " + shiftDate);
                e.printStackTrace();
            }
        }
    }
}