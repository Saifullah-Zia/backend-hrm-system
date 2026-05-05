package com.hrm.system.controller;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.repository.PayrollRepository;
import com.hrm.system.service.PayRollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayRollService payRollService;

    //create payroll
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayRollDto> createPayroll(@RequestBody PayRollDto dto){
        PayRollDto created = PayRollService.c
    }
}
