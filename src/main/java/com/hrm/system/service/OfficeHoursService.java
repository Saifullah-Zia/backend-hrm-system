package com.hrm.system.service;

import com.hrm.system.config.AppTimeZone;
import com.hrm.system.dto.OfficeHoursDto;
import com.hrm.system.model.OfficeHours;
import com.hrm.system.repository.OfficeHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class OfficeHoursService {

    private static final Long   SETTINGS_ID           = 1L;
    private static final String DEFAULT_START         = "17:00";
    private static final String DEFAULT_END           = "02:00";
    private static final int    DEFAULT_GRACE_MINUTES = 15;
    private static final String TIMEZONE              = AppTimeZone.PKT.getId(); // "Asia/Karachi"

    private final OfficeHoursRepository officeHoursRepository;

    public OfficeHoursService(OfficeHoursRepository officeHoursRepository) {
        this.officeHoursRepository = officeHoursRepository;
    }

    @Transactional(readOnly = true)
    public OfficeHoursDto get() {
        return officeHoursRepository.findById(SETTINGS_ID)
                .map(this::mapToDto)
                .orElse(new OfficeHoursDto(DEFAULT_START, DEFAULT_END, DEFAULT_GRACE_MINUTES, TIMEZONE));
    }

    @Transactional
    public OfficeHoursDto save(OfficeHoursDto dto) {
        OfficeHours entity = officeHoursRepository.findById(SETTINGS_ID)
                .orElse(new OfficeHours());

        entity.setId(SETTINGS_ID);
        entity.setWorkdayStart(dto.getWorkdayStart());
        entity.setWorkdayEnd(dto.getWorkdayEnd());
        entity.setGraceMinutes(dto.getGraceMinutes());

        return mapToDto(officeHoursRepository.save(entity));
    }

    /**
     * Calculates PRESENT or LATE based on PKT check-in time.
     * Fully handles overnight shifts (17:00 → 02:00).
     *
     * @param checkInPKT already converted to Pakistan time
     */
    public String calculateStatus(LocalTime checkInPKT) {
        OfficeHoursDto settings  = get();
        LocalTime shiftStart     = LocalTime.parse(settings.getWorkdayStart()); // 17:00
        LocalTime shiftEnd       = LocalTime.parse(settings.getWorkdayEnd());   // 02:00
        int       graceMinutes   = settings.getGraceMinutes();                  // 15
        LocalTime deadline       = shiftStart.plusMinutes(graceMinutes);        // 17:15

        // Overnight: end < start (02:00 < 17:00)
        boolean isOvernightShift     = shiftEnd.isBefore(shiftStart);
        // Grace crosses midnight: e.g. start=23:50 + 15min = 00:05
        boolean graceCrossesMidnight = deadline.isBefore(shiftStart);

        if (isOvernightShift) {
            // For overnight shifts, allow early arrivals (before shift start) to be PRESENT
            // Only mark LATE if check-in is after the grace deadline
            boolean beforeDeadline = !checkInPKT.isAfter(deadline);  // <= 17:15

            if (graceCrossesMidnight) {
                // Grace crosses midnight: check if after shift start OR before deadline (next day)
                boolean afterShiftStart = !checkInPKT.isBefore(shiftStart);
                return (afterShiftStart || beforeDeadline) ? "PRESENT" : "LATE";
            } else {
                // PRESENT if checked in before deadline (includes early arrivals)
                return beforeDeadline ? "PRESENT" : "LATE";
            }
        } else {
            // Regular day shift
            return checkInPKT.isAfter(deadline) ? "LATE" : "PRESENT";
        }
    }

    /**
     * Converts 24-hour string to 12-hour format.
     * "17:00" → "05:00 PM"
     * "02:00" → "02:00 AM"
     */
    public String formatTo12Hour(String time24) {
        LocalTime t = LocalTime.parse(time24);
        return t.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    private OfficeHoursDto mapToDto(OfficeHours entity) {
        return new OfficeHoursDto(
                entity.getWorkdayStart(),
                entity.getWorkdayEnd(),
                entity.getGraceMinutes(),
                TIMEZONE
        );
    }
}