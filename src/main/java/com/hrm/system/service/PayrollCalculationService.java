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

    public double calculateGrossSalary(double basicSalary, double totalAllowances, double totalBonuses) {
        return basicSalary + (totalAllowances > 0 ? totalAllowances : 0.0) + (totalBonuses > 0 ? totalBonuses : 0.0);
    }

    // NOTE: Removed misleading 5-parameter overload that accepted presentDays/paidLeaveDays
    // but never used them. All callers should use calculateGrossSalary(basicSalary, allowances, bonuses).

    public double calculateDeductions(int unpaidLeaveDays, int absentDays, int lateDays, 
                                      double dailySalary, double manualDeductions) {
        return calculateDeductions(unpaidLeaveDays, absentDays, lateDays, dailySalary, manualDeductions, 0.0);
    }

    public double calculateDeductions(int unpaidLeaveDays, int absentDays, int lateDays, 
                                      double dailySalary, double manualDeductions, double incomeTax) {
        double unpaidLeaveDeduction = applyUnpaidLeavePolicy(unpaidLeaveDays, dailySalary);
        double absentDeduction = applyAbsentPolicy(absentDays, dailySalary);
        double lateDeduction = applyLatePolicy(lateDays, dailySalary);
        return unpaidLeaveDeduction + absentDeduction + lateDeduction + manualDeductions + (incomeTax > 0 ? incomeTax : 0.0);
    }

    public double calculateNetSalary(double grossSalary, double totalDeductions) {
        return grossSalary - totalDeductions;
    }

    public double calculateIncomeTax(double monthlyTaxableSalary) {
        if (monthlyTaxableSalary <= 0) {
            return 0.0;
        }

        double annualTaxableIncome = monthlyTaxableSalary * 12.0;

        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isPresent() && policyOpt.get().getIncomeTaxRule() != null && !policyOpt.get().getIncomeTaxRule().trim().isEmpty()) {
            try {
                JsonNode rule = objectMapper.readTree(policyOpt.get().getIncomeTaxRule());
                if (rule.has("enabled") && !rule.get("enabled").asBoolean()) {
                    return 0.0;
                }

                if (rule.has("slabs") && rule.get("slabs").isArray()) {
                    double calculatedAnnualTax = 0.0;
                    boolean slabMatched = false;

                    for (JsonNode slab : rule.get("slabs")) {
                        double minAnnual = slab.has("minAnnual") ? slab.get("minAnnual").asDouble() : 0.0;
                        double maxAnnual = slab.has("maxAnnual") ? slab.get("maxAnnual").asDouble() : Double.MAX_VALUE;
                        double fixedTax = slab.has("fixedTax") ? slab.get("fixedTax").asDouble() : 0.0;
                        double taxRate = slab.has("taxRate") ? slab.get("taxRate").asDouble() : 0.0;

                        if (annualTaxableIncome > minAnnual && annualTaxableIncome <= maxAnnual) {
                            calculatedAnnualTax = fixedTax + (annualTaxableIncome - minAnnual) * (taxRate / 100.0);
                            slabMatched = true;
                            break;
                        }
                    }

                    if (slabMatched) {
                        return Math.max(0.0, Math.round((calculatedAnnualTax / 12.0) * 100.0) / 100.0);
                    }
                }
            } catch (Exception e) {
                // Fallback to standard FBR Pakistan slab calculation
            }
        }

        // Standard Pakistan FBR Salaried Slabs (Default Fallback)
        // Up to 600,000: 0%
        // 600,000 - 1,200,000: 5% of amount > 600,000
        // 1,200,000 - 2,200,000: 30,000 + 15% of amount > 1,200,000
        // 2,200,000 - 3,200,000: 180,000 + 25% of amount > 2,200,000
        // 3,200,000 - 4,100,000: 430,000 + 30% of amount > 3,200,000
        // Above 4,100,000: 700,000 + 35% of amount > 4,100,000
        double annualTax = 0.0;
        if (annualTaxableIncome <= 600000) {
            annualTax = 0.0;
        } else if (annualTaxableIncome <= 1200000) {
            annualTax = (annualTaxableIncome - 600000) * 0.05;
        } else if (annualTaxableIncome <= 2200000) {
            annualTax = 30000 + (annualTaxableIncome - 1200000) * 0.15;
        } else if (annualTaxableIncome <= 3200000) {
            annualTax = 180000 + (annualTaxableIncome - 2200000) * 0.25;
        } else if (annualTaxableIncome <= 4100000) {
            annualTax = 430000 + (annualTaxableIncome - 3200000) * 0.30;
        } else {
            annualTax = 700000 + (annualTaxableIncome - 4100000) * 0.35;
        }

        return Math.max(0.0, Math.round((annualTax / 12.0) * 100.0) / 100.0);
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
