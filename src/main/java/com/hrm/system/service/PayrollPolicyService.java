package com.hrm.system.service;

import com.hrm.system.dto.PayrollPolicyDto;
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

    @Autowired
    public PayrollPolicyService(PayrollPolicyRepository payrollPolicyRepository) {
        this.payrollPolicyRepository = payrollPolicyRepository;
    }

    @Transactional
    public PayrollPolicyDto createPolicy(PayrollPolicyDto dto) {
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
        policy.setIsActive(true);
        policy.setDescription(dto.getDescription());

        PayrollPolicy saved = payrollPolicyRepository.save(policy);
        return mapToDto(saved);
    }

    @Transactional
    public PayrollPolicyDto updatePolicy(Long policyId, PayrollPolicyDto dto) {
        PayrollPolicy policy = payrollPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Payroll policy not found"));

        policy.setLateDeductionRule(dto.getLateDeductionRule());
        policy.setUnpaidLeaveDeductionRule(dto.getUnpaidLeaveDeductionRule());
        policy.setAbsentDeductionRule(dto.getAbsentDeductionRule());
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
        dto.setIsActive(policy.getIsActive());
        dto.setDescription(policy.getDescription());
        dto.setCreatedAt(policy.getCreatedAt());
        dto.setUpdatedAt(policy.getUpdatedAt());
        return dto;
    }
}
