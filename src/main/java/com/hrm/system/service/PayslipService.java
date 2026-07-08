package com.hrm.system.service;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.PayrollItem;
import com.hrm.system.model.PayrollStatus;
import com.hrm.system.repository.PayrollItemRepository;
import com.hrm.system.repository.PayrollRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayslipService {

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;

    @Autowired
    public PayslipService(PayrollRepository payrollRepository, PayrollItemRepository payrollItemRepository) {
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
    }

    /**
     * Fetches payroll data for payslip generation, enforcing ownership and status rules.
     *
     * @param requestingUserId ID of the authenticated user making the request
     * @param isPrivileged     true if requester is ADMIN or SUPERADMIN
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPayslipData(Long payrollId, Long requestingUserId, boolean isPrivileged) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        if (!isPrivileged) {
            if (requestingUserId == null || !payroll.getUser().getId().equals(requestingUserId)) {
                throw new AccessDeniedException("You are not authorized to view this payslip");
            }
            if (payroll.getStatus() != PayrollStatus.APPROVED && payroll.getStatus() != PayrollStatus.PAID) {
                throw new AccessDeniedException("This payslip is not yet available for viewing");
            }
        }

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

    public String generatePayslipHtml(Long payrollId, Long requestingUserId, boolean isPrivileged) {
        Map<String, Object> data = getPayslipData(payrollId, requestingUserId, isPrivileged);

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

    public byte[] generatePayslipPdf(Long payrollId, Long requestingUserId, boolean isPrivileged) {
        Map<String, Object> data = getPayslipData(payrollId, requestingUserId, isPrivileged);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            document.add(new Paragraph("PAYSLIP")
                    .setFont(boldFont)
                    .setFontSize(24)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            document.add(new Paragraph(data.get("month") + " " + data.get("year"))
                    .setFont(font)
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30));

            document.add(new Paragraph("Employee Information")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setMarginBottom(10));

            Table employeeTable = new Table(2);
            employeeTable.addCell(createCell("Name:", boldFont));
            employeeTable.addCell(createCell(data.get("employeeName").toString(), font));
            employeeTable.addCell(createCell("Email:", boldFont));
            employeeTable.addCell(createCell(data.get("employeeEmail").toString(), font));
            document.add(employeeTable.setMarginBottom(20));

            document.add(new Paragraph("Attendance Summary")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setMarginBottom(10));

            Table attendanceTable = new Table(3);
            attendanceTable.addCell(createCell("Working Days:", boldFont));
            attendanceTable.addCell(createCell("Present Days:", boldFont));
            attendanceTable.addCell(createCell("Late Days:", boldFont));
            attendanceTable.addCell(createCell(data.get("workingDays").toString(), font));
            attendanceTable.addCell(createCell(data.get("presentDays").toString(), font));
            attendanceTable.addCell(createCell(data.get("lateDays").toString(), font));
            attendanceTable.addCell(createCell("Paid Leave:", boldFont));
            attendanceTable.addCell(createCell("Unpaid Leave:", boldFont));
            attendanceTable.addCell(createCell("Absent Days:", boldFont));
            attendanceTable.addCell(createCell(data.get("paidLeaveDays").toString(), font));
            attendanceTable.addCell(createCell(data.get("unpaidLeaveDays").toString(), font));
            attendanceTable.addCell(createCell(data.get("absentDays").toString(), font));
            document.add(attendanceTable.setMarginBottom(20));

            document.add(new Paragraph("Salary Breakdown")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setMarginBottom(10));

            Table salaryTable = new Table(2);
            salaryTable.addCell(createCell("Basic Salary:", boldFont));
            salaryTable.addCell(createCell("PKR " + String.format("%.2f", data.get("basicSalary")), font));
            salaryTable.addCell(createCell("Daily Salary:", boldFont));
            salaryTable.addCell(createCell("PKR " + String.format("%.2f", data.get("dailySalary")), font));
            salaryTable.addCell(createCell("Total Allowances:", boldFont));
            salaryTable.addCell(createCell("PKR " + String.format("%.2f", data.get("totalAllowances")), font));
            salaryTable.addCell(createCell("Total Bonuses:", boldFont));
            salaryTable.addCell(createCell("PKR " + String.format("%.2f", data.get("totalBonuses")), font));
            salaryTable.addCell(createCell("Total Deductions:", boldFont));
            Cell deductionCell = createCell("- PKR " + String.format("%.2f", data.get("totalDeductions")), font);
            deductionCell.setFontColor(ColorConstants.RED);
            salaryTable.addCell(deductionCell);
            salaryTable.addCell(createCell("Gross Salary:", boldFont));
            salaryTable.addCell(createCell("PKR " + String.format("%.2f", data.get("grossSalary")), boldFont));
            salaryTable.addCell(createCell("Net Salary:", boldFont));
            Cell netSalaryCell = createCell("PKR " + String.format("%.2f", data.get("netSalary")), boldFont);
            netSalaryCell.setFontColor(ColorConstants.GREEN);
            netSalaryCell.setFontSize(14);
            salaryTable.addCell(netSalaryCell);
            document.add(salaryTable.setMarginBottom(20));

            document.add(new Paragraph("Payment Status")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setMarginBottom(10));

            Table statusTable = new Table(2);
            statusTable.addCell(createCell("Status:", boldFont));
            statusTable.addCell(createCell(data.get("status").toString(), font));
            statusTable.addCell(createCell("Generated At:", boldFont));
            statusTable.addCell(createCell(data.get("generatedAt").toString(), font));
            statusTable.addCell(createCell("Approved At:", boldFont));
            statusTable.addCell(createCell(data.get("approvedAt") != null ? data.get("approvedAt").toString() : "N/A", font));
            statusTable.addCell(createCell("Paid At:", boldFont));
            statusTable.addCell(createCell(data.get("paidAt") != null ? data.get("paidAt").toString() : "N/A", font));
            document.add(statusTable);

            document.close();

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private Cell createCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font))
                .setPadding(5);
    }
}