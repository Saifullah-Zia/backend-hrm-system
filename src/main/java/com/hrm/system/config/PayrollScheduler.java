package com.hrm.system.config;

import com.hrm.system.service.PayRollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PayrollScheduler {

    @Autowired
    private PayRollService payRollService;

    // Runs at midnight on the last day of every month
    @Scheduled(cron = "0 0 0 L * *")
    public void generateMonthlyPayroll() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        System.out.println("Auto-generating payroll for: " + month + "/" + year);
        payRollService.generatePayrollForAllEmployees(month, year);
    }
}