package com.hrm.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FbrTax2026Test {

    @Mock
    private PayrollPolicyRepository payrollPolicyRepository;

    private PayrollCalculationService payrollCalculationService;

    @BeforeEach
    void setUp() {
        payrollCalculationService = new PayrollCalculationService(payrollPolicyRepository, new ObjectMapper());
        // Return empty policy to trigger standard FBR 2026 fallback slabs
        when(payrollPolicyRepository.findByIsActiveTrue()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("1. Salary Rs. 100,000/mo (Annual 1.2M) -> Monthly Tax = Rs. 500.00 (0.50% effective)")
    void testTax100k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("100000.00"));
        assertEquals(new BigDecimal("500.00"), tax);
    }

    @Test
    @DisplayName("2. Salary Rs. 200,000/mo (Annual 2.4M) -> Monthly Tax = Rs. 13,000.00 (6.50% effective)")
    void testTax200k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("200000.00"));
        assertEquals(new BigDecimal("13000.00"), tax);
    }

    @Test
    @DisplayName("3. Salary Rs. 250,000/mo (Annual 3.0M) -> Monthly Tax = Rs. 23,000.00 (9.20% effective)")
    void testTax250k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("250000.00"));
        assertEquals(new BigDecimal("23000.00"), tax);
    }

    @Test
    @DisplayName("4. Salary Rs. 300,000/mo (Annual 3.6M) -> Monthly Tax = Rs. 34,666.67 (11.56% effective)")
    void testTax300k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("300000.00"));
        assertEquals(new BigDecimal("34666.67"), tax);
    }

    @Test
    @DisplayName("5. Salary Rs. 400,000/mo (Annual 4.8M) -> Monthly Tax = Rs. 62,000.00 (15.50% effective)")
    void testTax400k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("400000.00"));
        assertEquals(new BigDecimal("62000.00"), tax);
    }

    @Test
    @DisplayName("6. Salary Rs. 500,000/mo (Annual 6.0M) -> Monthly Tax = Rs. 91,000.00 (18.20% effective)")
    void testTax500k() {
        BigDecimal tax = payrollCalculationService.calculateIncomeTax(new BigDecimal("500000.00"));
        assertEquals(new BigDecimal("91000.00"), tax);
    }
}
