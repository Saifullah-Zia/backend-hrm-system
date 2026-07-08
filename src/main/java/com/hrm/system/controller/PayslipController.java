package com.hrm.system.controller;

import com.hrm.system.service.PayslipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payslip")
public class PayslipController {

    private final PayslipService payslipService;

    @Autowired
    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    private Long getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    private boolean isPrivileged(HttpServletRequest request) {
        return request.isUserInRole("ADMIN") || request.isUserInRole("SUPERADMIN");
    }

    @GetMapping("/{payrollId}/data")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getPayslipData(@PathVariable Long payrollId, HttpServletRequest request) {
        Map<String, Object> data = payslipService.getPayslipData(payrollId, getRequestingUserId(request), isPrivileged(request));
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{payrollId}/html")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<String> getPayslipHtml(@PathVariable Long payrollId, HttpServletRequest request) {
        String html = payslipService.generatePayslipHtml(payrollId, getRequestingUserId(request), isPrivileged(request));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/{payrollId}/pdf")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public ResponseEntity<byte[]> getPayslipPdf(@PathVariable Long payrollId, HttpServletRequest request) {
        byte[] pdf = payslipService.generatePayslipPdf(payrollId, getRequestingUserId(request), isPrivileged(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip_" + payrollId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}