package com.hrm.system.service;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.PayrollItem;
import com.hrm.system.repository.PayrollItemRepository;
import com.hrm.system.repository.PayrollRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PayslipService {

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;

    @Autowired
    public PayslipService(PayrollRepository payrollRepository, PayrollItemRepository payrollItemRepository) {
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
    }

    public Map<String, Object> getPayslipData(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        List<PayrollItem> items = payrollItemRepository.findByPayrollId(payrollId);

        Map<String, Object> payslipData = new HashMap<>();
        payslipData.put("employeeId", payroll.getUser().getId());
        payslipData.put("employeeName", payroll.getUser().getName());
        payslipData.put("employeeEmail", payroll.getUser().getEmail());
        payslipData.put("month", payroll.getPayrollPeriod().getMonth());
        payslipData.put("year", payroll.getPayrollPeriod().getYear());
        payslipData.put("basicSalary", payroll.getBasicSalary());
        payslipData.put("dailySalary", payroll.getDailySalary());
        payslipData.put("workingDays", payroll.getWorkingDays());
        payslipData.put("presentDays", payroll.getPresentDays());
        payslipData.put("lateDays", payroll.getLateDays());
        payslipData.put("paidLeaveDays", payroll.getPaidLeaveDays());
        payslipData.put("unpaidLeaveDays", payroll.getUnpaidLeaveDays());
        payslipData.put("absentDays", payroll.getAbsentDays());
        payslipData.put("totalAllowances", payroll.getTotalAllowances());
        payslipData.put("totalBonuses", payroll.getTotalBonuses());
        payslipData.put("totalDeductions", payroll.getTotalDeductions());
        payslipData.put("grossSalary", payroll.getGrossSalary());
        payslipData.put("netSalary", payroll.getNetSalary());
        payslipData.put("status", payroll.getStatus().toString());
        payslipData.put("generatedAt", payroll.getGeneratedAt());
        payslipData.put("approvedAt", payroll.getApprovedAt());
        payslipData.put("paidAt", payroll.getPaidAt());
        payslipData.put("items", items);

        return payslipData;
    }

    public String generatePayslipHtml(Long payrollId) {
        Map<String, Object> data = getPayslipData(payrollId);
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("<title>Payslip - ").append(data.get("month")).append(" ").append(data.get("year")).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append(".header { text-align: center; margin-bottom: 30px; }\n");
        html.append(".section { margin-bottom: 20px; }\n");
        html.append(".label { font-weight: bold; }\n");
        html.append(".value { margin-left: 10px; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append(".total { font-weight: bold; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        html.append("<div class=\"header\">\n");
        html.append("<h1>PAYSLIP</h1>\n");
        html.append("<h2>").append(data.get("month")).append(" ").append(data.get("year")).append("</h2>\n");
        html.append("</div>\n");
        
        html.append("<div class=\"section\">\n");
        html.append("<h3>Employee Information</h3>\n");
        html.append("<p><span class=\"label\">Name:</span><span class=\"value\">").append(data.get("employeeName")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Email:</span><span class=\"value\">").append(data.get("employeeEmail")).append("</span></p>\n");
        html.append("</div>\n");
        
        html.append("<div class=\"section\">\n");
        html.append("<h3>Attendance Summary</h3>\n");
        html.append("<p><span class=\"label\">Working Days:</span><span class=\"value\">").append(data.get("workingDays")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Present Days:</span><span class=\"value\">").append(data.get("presentDays")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Late Days:</span><span class=\"value\">").append(data.get("lateDays")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Paid Leave Days:</span><span class=\"value\">").append(data.get("paidLeaveDays")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Unpaid Leave Days:</span><span class=\"value\">").append(data.get("unpaidLeaveDays")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Absent Days:</span><span class=\"value\">").append(data.get("absentDays")).append("</span></p>\n");
        html.append("</div>\n");
        
        html.append("<div class=\"section\">\n");
        html.append("<h3>Salary Breakdown</h3>\n");
        html.append("<p><span class=\"label\">Basic Salary:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("basicSalary"))).append("</span></p>\n");
        html.append("<p><span class=\"label\">Daily Salary:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("dailySalary"))).append("</span></p>\n");
        html.append("<p><span class=\"label\">Total Allowances:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("totalAllowances"))).append("</span></p>\n");
        html.append("<p><span class=\"label\">Total Bonuses:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("totalBonuses"))).append("</span></p>\n");
        html.append("<p><span class=\"label\">Total Deductions:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("totalDeductions"))).append("</span></p>\n");
        html.append("<p class=\"total\"><span class=\"label\">Gross Salary:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("grossSalary"))).append("</span></p>\n");
        html.append("<p class=\"total\"><span class=\"label\">Net Salary:</span><span class=\"value\">PKR ").append(String.format("%.2f", data.get("netSalary"))).append("</span></p>\n");
        html.append("</div>\n");
        
        html.append("<div class=\"section\">\n");
        html.append("<h3>Payment Status</h3>\n");
        html.append("<p><span class=\"label\">Status:</span><span class=\"value\">").append(data.get("status")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Generated At:</span><span class=\"value\">").append(data.get("generatedAt")).append("</span></p>\n");
        html.append("<p><span class=\"label\">Approved At:</span><span class=\"value\">").append(data.get("approvedAt") != null ? data.get("approvedAt") : "N/A").append("</span></p>\n");
        html.append("<p><span class=\"label\">Paid At:</span><span class=\"value\">").append(data.get("paidAt") != null ? data.get("paidAt") : "N/A").append("</span></p>\n");
        html.append("</div>\n");
        
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }

    public byte[] generatePayslipPdf(Long payrollId) {
        // PDF generation will be implemented in Phase 6
        // For now, return the HTML as bytes
        String html = generatePayslipHtml(payrollId);
        return html.getBytes();
    }
}
