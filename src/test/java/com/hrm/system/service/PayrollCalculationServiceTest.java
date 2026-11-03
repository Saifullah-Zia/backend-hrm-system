package com.hrm.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.PayrollPolicy;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PayrollCalculationServiceTest {

    @Mock
    private PayrollPolicyRepository payrollPolicyRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    private PayrollCalculationService calculationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculationService = new PayrollCalculationService(payrollPolicyRepository, objectMapper);
    }

    @Test
    @DisplayName("Flat percentage tax calculation with non-round numbers (4.75% of 137,500)")
    void testFlatPercentageNonRoundNumber() {
        String jsonRule = "{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": 4.75}";
        PayrollPolicy policy = new PayrollPolicy();
        policy.setIncomeTaxRule(jsonRule);
        when(payrollPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(policy));

        BigDecimal monthlySalary = BigDecimal.valueOf(137500);
        BigDecimal expectedTax = monthlySalary.multiply(BigDecimal.valueOf(0.0475)).setScale(2, RoundingMode.HALF_UP); // 6531.25

        BigDecimal actualTax = calculationService.calculateIncomeTax(monthlySalary);
        assertEquals(expectedTax, actualTax);
        assertEquals(new BigDecimal("6531.25"), actualTax);
        assertEquals("Income Tax (Flat Rate 4.75%)", calculationService.getAppliedTaxDescription(monthlySalary));
    }

    @Test
    @DisplayName("Disabled tax policy returns ZERO tax")
    void testDisabledTaxPolicy() {
        String jsonRule = "{\"enabled\": false, \"type\": \"PERCENTAGE\", \"percentage\": 5.0}";
        PayrollPolicy policy = new PayrollPolicy();
        policy.setIncomeTaxRule(jsonRule);
        when(payrollPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(policy));

        BigDecimal actualTax = calculationService.calculateIncomeTax(BigDecimal.valueOf(100000));
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), actualTax);
        assertEquals("Income Tax (Exempt)", calculationService.getAppliedTaxDescription(BigDecimal.valueOf(100000)));
    }

    @Test
    @DisplayName("Progressive slabs tax mode calculation")
    void testProgressiveSlabsTaxMode() {
        String jsonRule = "{\"enabled\": true, \"type\": \"SLABS\", \"slabs\": [" +
                "{\"minAnnual\": 0, \"maxAnnual\": 600000, \"fixedTax\": 0, \"taxRate\": 0}," +
                "{\"minAnnual\": 600000, \"maxAnnual\": 1200000, \"fixedTax\": 0, \"taxRate\": 5}" +
                "]}";
        PayrollPolicy policy = new PayrollPolicy();
        policy.setIncomeTaxRule(jsonRule);
        when(payrollPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(policy));

        // Salary = 85,000 monthly -> Annual = 1,020,000. Taxable over 600k = 420,000 @ 5% = 21,000 annual -> 1,750 monthly
        BigDecimal actualTax = calculationService.calculateIncomeTax(BigDecimal.valueOf(85000));
        assertEquals(new BigDecimal("1750.00"), actualTax);
        assertEquals("Income Tax (Progressive Slabs)", calculationService.getAppliedTaxDescription(BigDecimal.valueOf(85000)));
    }

    @Test
    @DisplayName("Legacy JSON without type field defaults to SLABS mode")
    void testLegacyJsonFallbackToSlabs() {
        String jsonRule = "{\"enabled\": true, \"slabs\": [" +
                "{\"minAnnual\": 0, \"maxAnnual\": 600000, \"fixedTax\": 0, \"taxRate\": 0}," +
                "{\"minAnnual\": 600000, \"maxAnnual\": 1200000, \"fixedTax\": 0, \"taxRate\": 5}" +
                "]}";
        PayrollPolicy policy = new PayrollPolicy();
        policy.setIncomeTaxRule(jsonRule);
        when(payrollPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.of(policy));

        BigDecimal actualTax = calculationService.calculateIncomeTax(BigDecimal.valueOf(85000));
        assertEquals(new BigDecimal("1750.00"), actualTax);
    }

    private static Payroll payroll(double basic, int workingDays, int absentDays) {
        Payroll p = new Payroll();
        p.setBasicSalary(basic);
        p.setWorkingDays(workingDays);
        p.setAbsentDays(absentDays);
        p.setDailySalary(BigDecimal.valueOf(basic).divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP).doubleValue());
        return p;
    }

    @Test
    @DisplayName("Recalculate: 4 of 22 days present (18 absent) pays only the attended days")
    void testRecalculateTotalsDeductsAbsences() {
        Payroll p = payroll(40000, 22, 18);

        BigDecimal tax = calculationService.recalculateTotals(p);

        assertEquals(0, tax.compareTo(BigDecimal.ZERO)); // 480,000/year is below the taxable threshold
        assertEquals(40000.0, p.getGrossSalary());
        assertEquals(32727.24, p.getTotalDeductions(), 0.001); // 18 x 1,818.18
        assertEquals(7272.76, p.getNetSalary(), 0.001);
    }

    @Test
    @DisplayName("Recalculate: HR FBR tax override and manual deduction replace the automatic tax")
    void testRecalculateTotalsUsesFbrOverride() {
        Payroll p = payroll(100000, 22, 0);
        p.setTotalBonuses(10000.0);
        p.setFbrTaxOverride(5000.0);
        p.setManualDeduction(1000.0);

        BigDecimal tax = calculationService.recalculateTotals(p);

        assertEquals(new BigDecimal("5000.00"), tax);
        assertEquals(110000.0, p.getGrossSalary());
        assertEquals(6000.0, p.getTotalDeductions());
        assertEquals(104000.0, p.getNetSalary());
    }

    @Test
    @DisplayName("Recalculate: automatic FBR tax is based on basic salary, not on bonuses")
    void testRecalculateTotalsTaxOnBasicOnly() {
        Payroll p = payroll(85000, 22, 0);
        p.setTotalBonuses(50000.0);

        BigDecimal tax = calculationService.recalculateTotals(p);

        assertEquals(new BigDecimal("350.00"), tax); // default FBR slabs: 1% of (1,020,000 - 600,000) / 12
        assertEquals(135000.0, p.getGrossSalary());
        assertEquals(134650.0, p.getNetSalary());
    }

    @Test
    @DisplayName("Recalculate: net salary never goes below zero")
    void testRecalculateTotalsNetNotNegative() {
        Payroll p = payroll(40000, 22, 22);
        p.setManualDeduction(5000.0);

        calculationService.recalculateTotals(p);

        assertEquals(0.0, p.getNetSalary());
    }
}
