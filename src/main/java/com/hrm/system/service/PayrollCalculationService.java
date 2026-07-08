package com.hrm.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.model.PayrollPolicy;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PayrollCalculationService {

    private final PayrollPolicyRepository payrollPolicyRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollCalculationService(PayrollPolicyRepository payrollPolicyRepository, ObjectMapper objectMapper) {
        this.payrollPolicyRepository = payrollPolicyRepository;
        this.objectMapper = objectMapper;
    }

    public double calculateDailySalary(double basicSalary, int workingDays) {
        if (workingDays <= 0) {
            throw new IllegalArgumentException("Working days must be greater than 0");
        }
        return basicSalary / workingDays;
    }

    public double calculateGrossSalary(double basicSalary, int presentDays, int paidLeaveDays, 
                                       double totalAllowances, double totalBonuses) {
        double dailySalary = calculateDailySalary(basicSalary, presentDays + paidLeaveDays);
        double attendanceSalary = dailySalary * (presentDays + paidLeaveDays);
        return attendanceSalary + totalAllowances + totalBonuses;
    }

    public double calculateDeductions(int unpaidLeaveDays, int absentDays, int lateDays, 
                                      double dailySalary, double manualDeductions) {
        double unpaidLeaveDeduction = applyUnpaidLeavePolicy(unpaidLeaveDays, dailySalary);
        double absentDeduction = applyAbsentPolicy(absentDays, dailySalary);
        double lateDeduction = applyLatePolicy(lateDays, dailySalary);
        return unpaidLeaveDeduction + absentDeduction + lateDeduction + manualDeductions;
    }

    public double calculateNetSalary(double grossSalary, double totalDeductions) {
        return grossSalary - totalDeductions;
    }

    public double applyLatePolicy(int lateCount, double dailySalary) {
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            // Default policy: first 3 lates free, then 100 PKR per late
            if (lateCount <= 3) {
                return 0.0;
            }
            return (lateCount - 3) * 100.0;
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getLateDeductionRule() != null && !policy.getLateDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getLateDeductionRule());
                int freeLates = rule.has("freeLates") ? rule.get("freeLates").asInt() : 3;
                double deductionPerLate = rule.has("deductionPerLate") ? rule.get("deductionPerLate").asDouble() : 100.0;
                
                if (lateCount <= freeLates) {
                    return 0.0;
                }
                return (lateCount - freeLates) * deductionPerLate;
            }
        } catch (Exception e) {
            // Fallback to default policy if JSON parsing fails
        }

        // Default fallback
        if (lateCount <= 3) {
            return 0.0;
        }
        return (lateCount - 3) * 100.0;
    }

    public double applyUnpaidLeavePolicy(int unpaidLeaveDays, double dailySalary) {
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            // Default policy: full deduction for each unpaid leave
            return unpaidLeaveDays * dailySalary;
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getUnpaidLeaveDeductionRule() != null && !policy.getUnpaidLeaveDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getUnpaidLeaveDeductionRule());
                double deductionPercentage = rule.has("deductionPercentage") 
                    ? rule.get("deductionPercentage").asDouble() : 100.0;
                
                return unpaidLeaveDays * dailySalary * (deductionPercentage / 100.0);
            }
        } catch (Exception e) {
            // Fallback to default policy
        }

        return unpaidLeaveDays * dailySalary;
    }

    public double applyAbsentPolicy(int absentDays, double dailySalary) {
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            // Default policy: full deduction for each absent day
            return absentDays * dailySalary;
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getAbsentDeductionRule() != null && !policy.getAbsentDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getAbsentDeductionRule());
                double deductionPercentage = rule.has("deductionPercentage") 
                    ? rule.get("deductionPercentage").asDouble() : 100.0;
                
                return absentDays * dailySalary * (deductionPercentage / 100.0);
            }
        } catch (Exception e) {
            // Fallback to default policy
        }

        return absentDays * dailySalary;
    }
}
