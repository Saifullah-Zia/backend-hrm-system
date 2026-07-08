package com.hrm.system.controller;

import com.hrm.system.service.PayslipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payslip")
@CrossOrigin(origins = "*")
public class PayslipController {

    private final PayslipService payslipService;

    @Autowired
    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @GetMapping("/{payrollId}/data")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getPayslipData(@PathVariable Long payrollId) {
        Map<String, Object> data = payslipService.getPayslipData(payrollId);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{payrollId}/html")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<String> getPayslipHtml(@PathVariable Long payrollId) {
        String html = payslipService.generatePayslipHtml(payrollId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/{payrollId}/pdf")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<byte[]> getPayslipPdf(@PathVariable Long payrollId) {
        byte[] pdf = payslipService.generatePayslipPdf(payrollId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip_" + payrollId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
