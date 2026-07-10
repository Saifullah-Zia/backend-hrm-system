package com.hrm.system.service;

import com.hrm.system.dto.HolidayDto;
import com.hrm.system.model.Holiday;
import com.hrm.system.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> getAllActiveHolidays() {
        return holidayRepository.findByIsActiveTrueOrderByDateAsc()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HolidayDto getHolidayById(Long id) {
        return holidayRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Holiday not found: " + id));
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        // Check exact date match
        if (holidayRepository.findByDateAndIsActiveTrue(date).isPresent()) {
            return true;
        }
        
        // Check recurring holidays (same month and day)
        return holidayRepository.findByIsActiveTrueOrderByDateAsc()
                .stream()
                .filter(h -> h.getIsRecurring())
                .anyMatch(h -> h.getDate().getMonth() == date.getMonth() && 
                             h.getDate().getDayOfMonth() == date.getDayOfMonth());
    }

    @Transactional
    public HolidayDto createHoliday(HolidayDto dto) {
        Holiday holiday = Holiday.builder()
                .name(dto.getName())
                .date(dto.getDate())
                .isRecurring(dto.getIsRecurring())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return mapToDto(holidayRepository.save(holiday));
    }

    @Transactional
    public HolidayDto updateHoliday(Long id, HolidayDto dto) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday not found: " + id));
        
        holiday.setName(dto.getName());
        holiday.setDate(dto.getDate());
        holiday.setIsRecurring(dto.getIsRecurring());
        if (dto.getIsActive() != null) {
            holiday.setIsActive(dto.getIsActive());
        }
        
        return mapToDto(holidayRepository.save(holiday));
    }

    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    private HolidayDto mapToDto(Holiday holiday) {
        return HolidayDto.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .date(holiday.getDate())
                .isRecurring(holiday.getIsRecurring())
                .isActive(holiday.getIsActive())
                .build();
    }
}
