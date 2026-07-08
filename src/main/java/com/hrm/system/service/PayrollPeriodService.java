package com.hrm.system.service;

import com.hrm.system.dto.PayrollPeriodDto;
import com.hrm.system.model.PayrollPeriod;
import com.hrm.system.model.User;
import com.hrm.system.repository.PayrollPeriodRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final UserRepository userRepository;

    @Autowired
    public PayrollPeriodService(PayrollPeriodRepository payrollPeriodRepository, UserRepository userRepository) {
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PayrollPeriodDto createPayrollPeriod(String month, Integer year, String company, String department) {
        // Check if period already exists
        if (department != null && !department.isEmpty()) {
            if (payrollPeriodRepository.existsByMonthAndYearAndDepartment(month, year, department)) {
                throw new RuntimeException("Payroll period already exists for this month, year, and department");
            }
        } else {
            Optional<PayrollPeriod> existing = payrollPeriodRepository.findByMonthAndYear(month, year);
            if (existing.isPresent() && existing.get().getDepartment() == null) {
                throw new RuntimeException("Payroll period already exists for this month and year");
            }
        }

        PayrollPeriod payrollPeriod = new PayrollPeriod();
        payrollPeriod.setMonth(month);
        payrollPeriod.setYear(year);
        payrollPeriod.setCompany(company);
        payrollPeriod.setDepartment(department);
        payrollPeriod.setLocked(false);

        PayrollPeriod saved = payrollPeriodRepository.save(payrollPeriod);
        return mapToDto(saved);
    }

    @Transactional
    public PayrollPeriodDto lockPayrollPeriod(Long periodId, Long userId) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        if (payrollPeriod.getLocked()) {
            throw new RuntimeException("Payroll period is already locked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        payrollPeriod.setLocked(true);
        payrollPeriod.setLockedBy(user);
        payrollPeriod.setLockedAt(LocalDateTime.now());

        PayrollPeriod saved = payrollPeriodRepository.save(payrollPeriod);
        return mapToDto(saved);
    }

    @Transactional
    public PayrollPeriodDto unlockPayrollPeriod(Long periodId, Long userId) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));

        if (!payrollPeriod.getLocked()) {
            throw new RuntimeException("Payroll period is not locked");
        }

        payrollPeriod.setLocked(false);
        payrollPeriod.setUnlockedBy(userId);
        payrollPeriod.setUnlockedAt(LocalDateTime.now());
        payrollPeriod.setLockedBy(null);
        payrollPeriod.setLockedAt(null);

        PayrollPeriod saved = payrollPeriodRepository.save(payrollPeriod);
        return mapToDto(saved);
    }

    public boolean isPeriodLocked(String month, Integer year, String department) {
        Optional<PayrollPeriod> period;
        if (department != null && !department.isEmpty()) {
            period = payrollPeriodRepository.findByMonthAndYearAndDepartment(month, year, department);
        } else {
            period = payrollPeriodRepository.findByMonthAndYear(month, year);
        }
        return period.isPresent() && period.get().getLocked();
    }

    public Optional<PayrollPeriodDto> getPayrollPeriod(String month, Integer year, String department) {
        Optional<PayrollPeriod> period;
        if (department != null && !department.isEmpty()) {
            period = payrollPeriodRepository.findByMonthAndYearAndDepartment(month, year, department);
        } else {
            period = payrollPeriodRepository.findByMonthAndYear(month, year);
        }
        return period.map(this::mapToDto);
    }

    public List<PayrollPeriodDto> getAllPayrollPeriods() {
        return payrollPeriodRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public PayrollPeriodDto getPayrollPeriodById(Long id) {
        PayrollPeriod payrollPeriod = payrollPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll period not found"));
        return mapToDto(payrollPeriod);
    }

    private PayrollPeriodDto mapToDto(PayrollPeriod payrollPeriod) {
        PayrollPeriodDto dto = new PayrollPeriodDto();
        dto.setId(payrollPeriod.getId());
        dto.setMonth(payrollPeriod.getMonth());
        dto.setYear(payrollPeriod.getYear());
        dto.setCompany(payrollPeriod.getCompany());
        dto.setDepartment(payrollPeriod.getDepartment());
        dto.setLocked(payrollPeriod.getLocked());
        dto.setLockedBy(payrollPeriod.getLockedBy() != null ? payrollPeriod.getLockedBy().getId() : null);
        dto.setLockedByName(payrollPeriod.getLockedBy() != null ? payrollPeriod.getLockedBy().getName() : null);
        dto.setLockedAt(payrollPeriod.getLockedAt());
        dto.setUnlockedBy(payrollPeriod.getUnlockedBy());
        dto.setUnlockedAt(payrollPeriod.getUnlockedAt());
        dto.setCreatedAt(payrollPeriod.getCreatedAt());
        dto.setUpdatedAt(payrollPeriod.getUpdatedAt());
        return dto;
    }
}
