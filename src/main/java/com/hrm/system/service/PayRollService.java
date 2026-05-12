package com.hrm.system.service;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.User;
import com.hrm.system.repository.PayrollRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PayRollService {

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    // ─── Generate payroll for all employees ──────────────────────────────────

    public void generatePayrollForAllEmployees(int month, int year) {
        List<User> employees = userRepository.findAll();

        for (User employee : employees) {
            String monthStr = String.format("%d-%02d", year, month);

            boolean exists = payrollRepository.existsByUserAndMonth(employee, monthStr);

            if (!exists) {
                Payroll payroll = new Payroll();
                payroll.setUser(employee);
                payroll.setMonth(monthStr);
                payroll.setYear(year);
                payroll.setSalary(employee.getBasicSalary());
                payroll.setBonuses(0.0);
                payroll.setDeduction(0.0);
                payroll.setNetSalary(employee.getBasicSalary());
                payroll.setStatus("PENDING");

                Payroll saved = payrollRepository.save(payroll);

                notificationService.createNotification(
                        employee.getId(),
                        String.format("💰 Your payroll for %s has been generated. Net salary: %.2f",
                                monthStr, employee.getBasicSalary()),
                        "PAYROLL",
                        employee.getId(),
                        saved.getId()
                );

                try {
                    emailService.sendPayrollNotification(employee.getEmail(), monthStr, year);
                    System.out.println("✓ Email sent successfully to: " + employee.getEmail());
                } catch (Exception e) {
                    System.err.println("✗ Email failed for: " + employee.getEmail());
                    System.err.println("Error details: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    public PayRollDto createPayroll(PayRollDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getUserId()));

        Payroll payroll = new Payroll();
        payroll.setUser(user);
        payroll.setSalary(dto.getSalary() != null ? dto.getSalary() : 0.0);
        payroll.setBonuses(dto.getBonuses() != null ? dto.getBonuses() : 0.0);
        payroll.setDeduction(dto.getDeductions() != null ? dto.getDeductions() : 0.0);
        payroll.setNetSalary(calculateNetSalary(
                payroll.getSalary(),
                payroll.getBonuses(),
                payroll.getDeduction()
        ));
        payroll.setMonth(dto.getMonth());
        payroll.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        payroll.setYear(Integer.parseInt(dto.getMonth().split("-")[0]));

        Payroll saved = payrollRepository.save(payroll);

        // Notify employee about payroll creation
        notificationService.createNotification(
                user.getId(),
                String.format("💰 Your payroll for %s has been created. Net salary: %.2f",
                        dto.getMonth(), saved.getNetSalary()),
                "PAYROLL",
                user.getId(),
                saved.getId()
        );

        // Send email to employee
        try {
            int year = Integer.parseInt(dto.getMonth().split("-")[0]);
            emailService.sendPayrollNotification(user.getEmail(), dto.getMonth(), year);
            System.out.println("✓ Email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("✗ Email failed for: " + user.getEmail());
            System.err.println("Error details: " + e.getMessage());
            e.printStackTrace();
        }

        return mapToDto(saved);
    }

    // ─── Read all ─────────────────────────────────────────────────────────────

    public List<PayRollDto> getAllPayroll() {
        return payrollRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ─── Read by ID ───────────────────────────────────────────────────────────

    public PayRollDto getPayrollById(long id) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));
        return mapToDto(payroll);
    }

    // ─── Read by user ID ──────────────────────────────────────────────────────

    public List<PayRollDto> getPayrollByUserId(Long userId) {
        return payrollRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public PayRollDto updatePayroll(Long id, PayRollDto dto) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll not found for ID: " + id));

        payroll.setSalary(dto.getSalary() != null ? dto.getSalary() : 0.0);
        payroll.setBonuses(dto.getBonuses() != null ? dto.getBonuses() : 0.0);
        payroll.setDeduction(dto.getDeductions() != null ? dto.getDeductions() : 0.0);
        payroll.setNetSalary(calculateNetSalary(
                payroll.getSalary(),
                payroll.getBonuses(),
                payroll.getDeduction()
        ));
        payroll.setMonth(dto.getMonth());
        payroll.setStatus(dto.getStatus() != null ? dto.getStatus() : payroll.getStatus());

        Payroll saved = payrollRepository.save(payroll);

        notificationService.createNotification(
                payroll.getUser().getId(),
                String.format("💰 Your payroll for %s has been updated. Net salary: %.2f",
                        saved.getMonth(), saved.getNetSalary()),
                "PAYROLL",
                payroll.getUser().getId(),
                saved.getId()
        );

        return mapToDto(saved);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    public void deletePayroll(Long id) {
        if (!payrollRepository.existsById(id)) {
            throw new RuntimeException("Payroll not found for ID: " + id);
        }
        payrollRepository.deleteById(id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public double calculateNetSalary(Double salary, Double bonuses, Double deductions) {
        double sal = salary != null ? salary : 0.0;
        double bon = bonuses != null ? bonuses : 0.0;
        double ded = deductions != null ? deductions : 0.0;
        return sal + bon - ded;
    }

    public PayRollDto mapToDto(Payroll payroll) {
        PayRollDto dto = new PayRollDto();
        dto.setId(payroll.getId());
        dto.setUserId(payroll.getUser().getId());
        dto.setUserName(payroll.getUser().getName());
        dto.setSalary(payroll.getSalary());
        dto.setBonuses(payroll.getBonuses());
        dto.setDeductions(payroll.getDeduction());
        dto.setNetSalary(payroll.getNetSalary());
        dto.setMonth(payroll.getMonth());
        dto.setStatus(payroll.getStatus());
        return dto;
    }
}