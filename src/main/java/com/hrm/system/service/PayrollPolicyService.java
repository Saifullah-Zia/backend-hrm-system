package com.hrm.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.system.dto.PayrollPolicyDto;
import com.hrm.system.enumm.AuditAction;
import com.hrm.system.model.PayrollPolicy;
import com.hrm.system.repository.PayrollPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayrollPolicyService {

    private final PayrollPolicyRepository payrollPolicyRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollPolicyService(PayrollPolicyRepository payrollPolicyRepository,
                                AuditLogService auditLogService,
                                ObjectMapper objectMapper) {
        this.payrollPolicyRepository = payrollPolicyRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public void validateIncomeTaxRule(String incomeTaxRule) {
        if (incomeTaxRule == null || incomeTaxRule.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode rule = objectMapper.readTree(incomeTaxRule);
            boolean isPercentageType = rule.has("type") && "PERCENTAGE".equalsIgnoreCase(rule.get("type").asText());
            if (isPercentageType || (rule.has("percentage") && !rule.has("type"))) {
                if (!rule.has("percentage") || rule.get("percentage").isNull()) {
                    throw new IllegalArgumentException("Tax percentage must be specified for flat percentage mode");
                }
                double pct = rule.get("percentage").asDouble();
                if (pct < 0.0 || pct > 100.0) {
                    throw new IllegalArgumentException("Tax percentage must be between 0.0 and 100.0");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignored) {}
    }

    @Transactional
    public PayrollPolicyDto createPolicy(PayrollPolicyDto dto) {
        validateIncomeTaxRule(dto.getIncomeTaxRule());

        // Deactivate any existing active policy
        Optional<PayrollPolicy> existingActive = payrollPolicyRepository.findByIsActiveTrue();
        existingActive.ifPresent(policy -> {
            policy.setIsActive(false);
            payrollPolicyRepository.save(policy);
        });

        PayrollPolicy policy = new PayrollPolicy();
        policy.setLateDeductionRule(dto.getLateDeductionRule());
        policy.setUnpaidLeaveDeductionRule(dto.getUnpaidLeaveDeductionRule());
        policy.setAbsentDeductionRule(dto.getAbsentDeductionRule());
        policy.setIncomeTaxRule(dto.getIncomeTaxRule());
        policy.setIsActive(true);
        policy.setDescription(dto.getDescription());

        PayrollPolicy saved = payrollPolicyRepository.save(policy);

        if (auditLogService != null) {
            auditLogService.log("PayrollPolicy", saved.getId(), AuditAction.CREATE,
                    "Created new active payroll policy: " + saved.getDescription(), 1L);
        }

        return mapToDto(saved);
    }

    @Transactional
    public PayrollPolicyDto updatePolicy(Long policyId, PayrollPolicyDto dto) {
        validateIncomeTaxRule(dto.getIncomeTaxRule());

        PayrollPolicy policy = payrollPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Payroll policy not found"));

        policy.setLateDeductionRule(dto.getLateDeductionRule());
        policy.setUnpaidLeaveDeductionRule(dto.getUnpaidLeaveDeductionRule());
        policy.setAbsentDeductionRule(dto.getAbsentDeductionRule());
        policy.setIncomeTaxRule(dto.getIncomeTaxRule());
        policy.setIsActive(dto.getIsActive());
        policy.setDescription(dto.getDescription());

        // If activating this policy, deactivate others
        if (dto.getIsActive()) {
            List<PayrollPolicy> otherPolicies = payrollPolicyRepository.findAll().stream()
                    .filter(p -> !p.getId().equals(policyId) && p.getIsActive())
                    .collect(Collectors.toList());
            otherPolicies.forEach(p -> {
                p.setIsActive(false);
                payrollPolicyRepository.save(p);
            });
        }

        PayrollPolicy saved = payrollPolicyRepository.save(policy);

        if (auditLogService != null) {
            auditLogService.log("PayrollPolicy", saved.getId(), AuditAction.UPDATE,
                    "Updated payroll policy rules: " + saved.getDescription(), 1L);
        }

        return mapToDto(saved);
    }

    public Optional<PayrollPolicyDto> getActivePolicy() {
        return payrollPolicyRepository.findByIsActiveTrue().map(this::mapToDto);
    }

    public PayrollPolicyDto getPolicyById(Long id) {
        PayrollPolicy policy = payrollPolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payroll policy not found"));
        return mapToDto(policy);
    }

    public List<PayrollPolicyDto> getAllPolicies() {
        return payrollPolicyRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PayrollPolicyDto mapToDto(PayrollPolicy policy) {
        PayrollPolicyDto dto = new PayrollPolicyDto();
        dto.setId(policy.getId());
        dto.setLateDeductionRule(policy.getLateDeductionRule());
        dto.setUnpaidLeaveDeductionRule(policy.getUnpaidLeaveDeductionRule());
        dto.setAbsentDeductionRule(policy.getAbsentDeductionRule());
        dto.setIncomeTaxRule(policy.getIncomeTaxRule());
        dto.setIsActive(policy.getIsActive());
        dto.setDescription(policy.getDescription());
        dto.setCreatedAt(policy.getCreatedAt());
        dto.setUpdatedAt(policy.getUpdatedAt());
        return dto;
    }
}
