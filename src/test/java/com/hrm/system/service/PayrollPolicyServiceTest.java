package com.hrm.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.dto.PayrollPolicyDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.PayrollPolicy;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayrollPolicyServiceTest {

    @Mock
    private PayrollPolicyRepository payrollPolicyRepository;

    @Mock
    private AuditLogService auditLogService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private PayrollPolicyService policyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        policyService = new PayrollPolicyService(payrollPolicyRepository, auditLogService, objectMapper);
    }

    @Test
    @DisplayName("Valid percentage (100.0) is accepted")
    void testValidBoundaryPercentage() {
        assertDoesNotThrow(() -> policyService.validateIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": 100.0}"));
        assertDoesNotThrow(() -> policyService.validateIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": 0.0}"));
    }

    @Test
    @DisplayName("Negative percentage (-1.0) throws IllegalArgumentException")
    void testNegativePercentageRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> policyService.validateIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": -1.0}"));
        assertEquals("Tax percentage must be between 0.0 and 100.0", ex.getMessage());
    }

    @Test
    @DisplayName("Over-100 percentage (100.01) throws IllegalArgumentException")
    void testOver100PercentageRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> policyService.validateIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": 100.01}"));
        assertEquals("Tax percentage must be between 0.0 and 100.0", ex.getMessage());
    }

    @Test
    @DisplayName("Null percentage when type=PERCENTAGE throws IllegalArgumentException")
    void testNullPercentageRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> policyService.validateIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\"}"));
        assertEquals("Tax percentage must be specified for flat percentage mode", ex.getMessage());
    }

    @Test
    @DisplayName("Policy update triggers auditLogService log call")
    void testPolicyUpdateDispatchesAuditLog() {
        PayrollPolicy existing = new PayrollPolicy();
        existing.setId(1L);
        existing.setIsActive(true);
        existing.setDescription("Old Policy");

        when(payrollPolicyRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(payrollPolicyRepository.save(any(PayrollPolicy.class))).thenAnswer(i -> i.getArgument(0));

        PayrollPolicyDto updateDto = new PayrollPolicyDto();
        updateDto.setId(1L);
        updateDto.setDescription("Updated Policy");
        updateDto.setIncomeTaxRule("{\"enabled\": true, \"type\": \"PERCENTAGE\", \"percentage\": 5.0}");
        updateDto.setIsActive(true);

        policyService.updatePolicy(1L, updateDto);

        verify(auditLogService, times(1)).log(
                eq("PayrollPolicy"),
                eq(1L),
                eq(AuditAction.UPDATE),
                contains("Updated payroll policy rules"),
                anyLong()
        );
    }
}
