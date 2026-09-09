package com.hrm.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
