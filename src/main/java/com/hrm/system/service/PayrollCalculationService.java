package com.hrm.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.model.PayrollPolicy;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public BigDecimal calculateDailySalary(BigDecimal basicSalary, int workingDays) {
        if (workingDays <= 0) {
            throw new IllegalArgumentException("Working days must be greater than 0");
        }
        if (basicSalary == null || basicSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return basicSalary.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateGrossSalary(BigDecimal basicSalary, BigDecimal totalAllowances, BigDecimal totalBonuses) {
        BigDecimal basic = basicSalary != null && basicSalary.compareTo(BigDecimal.ZERO) > 0 ? basicSalary : BigDecimal.ZERO;
        BigDecimal allowances = totalAllowances != null && totalAllowances.compareTo(BigDecimal.ZERO) > 0 ? totalAllowances : BigDecimal.ZERO;
        BigDecimal bonuses = totalBonuses != null && totalBonuses.compareTo(BigDecimal.ZERO) > 0 ? totalBonuses : BigDecimal.ZERO;
        return basic.add(allowances).add(bonuses).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDeductions(int unpaidLeaveDays, int absentDays, int lateDays,
                                       BigDecimal dailySalary, BigDecimal manualDeductions) {
        return calculateDeductions(unpaidLeaveDays, absentDays, lateDays, dailySalary, manualDeductions, BigDecimal.ZERO);
    }

    public BigDecimal calculateDeductions(int unpaidLeaveDays, int absentDays, int lateDays,
                                       BigDecimal dailySalary, BigDecimal manualDeductions, BigDecimal incomeTax) {
        BigDecimal daily = dailySalary != null ? dailySalary : BigDecimal.ZERO;
        BigDecimal manual = manualDeductions != null && manualDeductions.compareTo(BigDecimal.ZERO) > 0 ? manualDeductions : BigDecimal.ZERO;
        BigDecimal tax = incomeTax != null && incomeTax.compareTo(BigDecimal.ZERO) > 0 ? incomeTax : BigDecimal.ZERO;

        BigDecimal unpaidLeaveDeduction = applyUnpaidLeavePolicy(unpaidLeaveDays, daily);
        BigDecimal absentDeduction = applyAbsentPolicy(absentDays, daily);
        BigDecimal lateDeduction = applyLatePolicy(lateDays, daily);

        return unpaidLeaveDeduction.add(absentDeduction).add(lateDeduction).add(manual).add(tax).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNetSalary(BigDecimal grossSalary, BigDecimal totalDeductions) {
        BigDecimal gross = grossSalary != null ? grossSalary : BigDecimal.ZERO;
        BigDecimal deductions = totalDeductions != null ? totalDeductions : BigDecimal.ZERO;
        return gross.subtract(deductions).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateIncomeTax(BigDecimal monthlyTaxableSalary) {
        if (monthlyTaxableSalary == null || monthlyTaxableSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isPresent() && policyOpt.get().getIncomeTaxRule() != null && !policyOpt.get().getIncomeTaxRule().trim().isEmpty()) {
            try {
                JsonNode rule = objectMapper.readTree(policyOpt.get().getIncomeTaxRule());
                if (rule.has("enabled") && !rule.get("enabled").asBoolean()) {
                    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }

                // Flat Percentage Mode
                boolean isPercentageType = rule.has("type") && "PERCENTAGE".equalsIgnoreCase(rule.get("type").asText());
                if (isPercentageType || (rule.has("percentage") && !rule.has("type"))) {
                    double pct = rule.has("percentage") ? rule.get("percentage").asDouble() : 0.0;
                    if (pct <= 0) {
                        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                    BigDecimal rate = BigDecimal.valueOf(pct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                    return monthlyTaxableSalary.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }

                // Progressive Slabs Mode
                if (rule.has("slabs") && rule.get("slabs").isArray()) {
                    BigDecimal annualTaxableIncome = monthlyTaxableSalary.multiply(BigDecimal.valueOf(12));
                    BigDecimal calculatedAnnualTax = BigDecimal.ZERO;
                    boolean slabMatched = false;

                    for (JsonNode slab : rule.get("slabs")) {
                        BigDecimal minAnnual = slab.has("minAnnual") ? BigDecimal.valueOf(slab.get("minAnnual").asDouble()) : BigDecimal.ZERO;
                        BigDecimal maxAnnual = slab.has("maxAnnual") ? BigDecimal.valueOf(slab.get("maxAnnual").asDouble()) : BigDecimal.valueOf(Double.MAX_VALUE);
                        BigDecimal fixedTax = slab.has("fixedTax") ? BigDecimal.valueOf(slab.get("fixedTax").asDouble()) : BigDecimal.ZERO;
                        double taxRate = slab.has("taxRate") ? slab.get("taxRate").asDouble() : 0.0;

                        if (annualTaxableIncome.compareTo(minAnnual) > 0 && annualTaxableIncome.compareTo(maxAnnual) <= 0) {
                            BigDecimal excess = annualTaxableIncome.subtract(minAnnual);
                            BigDecimal variableTax = excess.multiply(BigDecimal.valueOf(taxRate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                            calculatedAnnualTax = fixedTax.add(variableTax);
                            slabMatched = true;
                            break;
                        }
                    }

                    if (slabMatched) {
                        return calculatedAnnualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                    }
                }
            } catch (Exception e) {
                // Fallback to standard FBR Pakistan slab calculation
            }
        }

        // Standard Pakistan FBR Salaried Slabs (Default Fallback)
        BigDecimal annualTaxableIncome = monthlyTaxableSalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTax = BigDecimal.ZERO;
        double annualVal = annualTaxableIncome.doubleValue();

        if (annualVal <= 600000) {
            annualTax = BigDecimal.ZERO;
        } else if (annualVal <= 1200000) {
            annualTax = annualTaxableIncome.subtract(BigDecimal.valueOf(600000)).multiply(BigDecimal.valueOf(0.05));
        } else if (annualVal <= 2200000) {
            annualTax = BigDecimal.valueOf(30000).add(annualTaxableIncome.subtract(BigDecimal.valueOf(1200000)).multiply(BigDecimal.valueOf(0.15)));
        } else if (annualVal <= 3200000) {
            annualTax = BigDecimal.valueOf(180000).add(annualTaxableIncome.subtract(BigDecimal.valueOf(2200000)).multiply(BigDecimal.valueOf(0.25)));
        } else if (annualVal <= 4100000) {
            annualTax = BigDecimal.valueOf(430000).add(annualTaxableIncome.subtract(BigDecimal.valueOf(3200000)).multiply(BigDecimal.valueOf(0.30)));
        } else {
            annualTax = BigDecimal.valueOf(700000).add(annualTaxableIncome.subtract(BigDecimal.valueOf(4100000)).multiply(BigDecimal.valueOf(0.35)));
        }

        return annualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public String getAppliedTaxDescription(BigDecimal monthlyTaxableSalary) {
        if (monthlyTaxableSalary == null || monthlyTaxableSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return "Monthly withholding tax";
        }

        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isPresent() && policyOpt.get().getIncomeTaxRule() != null && !policyOpt.get().getIncomeTaxRule().trim().isEmpty()) {
            try {
                JsonNode rule = objectMapper.readTree(policyOpt.get().getIncomeTaxRule());
                if (rule.has("enabled") && !rule.get("enabled").asBoolean()) {
                    return "Income Tax (Exempt)";
                }
                boolean isPercentageType = rule.has("type") && "PERCENTAGE".equalsIgnoreCase(rule.get("type").asText());
                if (isPercentageType || (rule.has("percentage") && !rule.has("type"))) {
                    double pct = rule.has("percentage") ? rule.get("percentage").asDouble() : 0.0;
                    return String.format("Income Tax (Flat Rate %.2f%%)", pct);
                }
                return "Income Tax (Progressive Slabs)";
            } catch (Exception ignored) {}
        }
        return "Income Tax (Progressive Slabs)";
    }

    public BigDecimal applyLatePolicy(int lateCount, BigDecimal dailySalary) {
        BigDecimal daily = dailySalary != null ? dailySalary : BigDecimal.ZERO;
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            if (lateCount <= 3) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return BigDecimal.valueOf((lateCount - 3) * 100.0).setScale(2, RoundingMode.HALF_UP);
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getLateDeductionRule() != null && !policy.getLateDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getLateDeductionRule());
                int freeLates = rule.has("freeLates") ? rule.get("freeLates").asInt() : 3;
                double deductionPerLate = rule.has("deductionPerLate") ? rule.get("deductionPerLate").asDouble() : 100.0;
                
                if (lateCount <= freeLates) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                return BigDecimal.valueOf((lateCount - freeLates) * deductionPerLate).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {}

        if (lateCount <= 3) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf((lateCount - 3) * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal applyUnpaidLeavePolicy(int unpaidLeaveDays, BigDecimal dailySalary) {
        BigDecimal daily = dailySalary != null ? dailySalary : BigDecimal.ZERO;
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            return daily.multiply(BigDecimal.valueOf(unpaidLeaveDays)).setScale(2, RoundingMode.HALF_UP);
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getUnpaidLeaveDeductionRule() != null && !policy.getUnpaidLeaveDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getUnpaidLeaveDeductionRule());
                double deductionPercentage = rule.has("deductionPercentage") 
                    ? rule.get("deductionPercentage").asDouble() : 100.0;
                
                BigDecimal rate = BigDecimal.valueOf(deductionPercentage).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                return daily.multiply(BigDecimal.valueOf(unpaidLeaveDays)).multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {}

        return daily.multiply(BigDecimal.valueOf(unpaidLeaveDays)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal applyAbsentPolicy(int absentDays, BigDecimal dailySalary) {
        BigDecimal daily = dailySalary != null ? dailySalary : BigDecimal.ZERO;
        Optional<PayrollPolicy> policyOpt = payrollPolicyRepository.findByIsActiveTrue();
        if (policyOpt.isEmpty()) {
            return daily.multiply(BigDecimal.valueOf(absentDays)).setScale(2, RoundingMode.HALF_UP);
        }

        try {
            PayrollPolicy policy = policyOpt.get();
            if (policy.getAbsentDeductionRule() != null && !policy.getAbsentDeductionRule().isEmpty()) {
                JsonNode rule = objectMapper.readTree(policy.getAbsentDeductionRule());
                double deductionPercentage = rule.has("deductionPercentage") 
                    ? rule.get("deductionPercentage").asDouble() : 100.0;
                
                BigDecimal rate = BigDecimal.valueOf(deductionPercentage).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                return daily.multiply(BigDecimal.valueOf(absentDays)).multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {}

        return daily.multiply(BigDecimal.valueOf(absentDays)).setScale(2, RoundingMode.HALF_UP);
    }
}
