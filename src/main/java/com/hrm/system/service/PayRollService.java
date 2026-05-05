package com.hrm.system.service;

import com.hrm.system.dto.PayRollDto;
import com.hrm.system.model.Payroll;
import com.hrm.system.model.User;
import com.hrm.system.repository.AttendanceRepository;
import com.hrm.system.repository.PayrollRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PayRollService {

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    //Create payroll for user
    public PayRollDto createPayroll(PayRollDto dto){
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getUserId()));

        Payroll payroll = new Payroll();
        payroll.setUser(user);
        payroll.setSalary(dto.getSalary());
        payroll.setBonuses(dto.getBonuses());
        payroll.setNetSalary(calculateNetSalary(dto.getSalary(), dto.getBonuses(), dto.getDeductions()));
        payroll.setDeduction(dto.getDeductions());
        payroll.setMonth(dto.getMonth());

        Payroll saved = payrollRepository.save(payroll);
        return mapToDto(saved);
    }

    //get all payrolls
    public List<PayRollDto> getAllPayroll(){
        return payrollRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    //get payroll by id
    public  PayRollDto getPayrollById(long id){
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("payroll not found for this ID"+ id));
        return mapToDto(payroll);

    }

    // get payroll by user id
    public List<PayRollDto> getPayrollByUserId(Long id){
        return payrollRepository.findById(id)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

    }

    //Update payroll
    public PayRollDto updatePayroll(Long id, PayRollDto dto){
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("payroll not found on this id" + id));
        payroll.setSalary(dto.getSalary());
        payroll.setBonuses(dto.getBonuses());
        payroll.setDeduction(dto.getDeductions());
        payroll.setNetSalary(calculateNetSalary(dto.getSalary(), dto.getBonuses(), dto.getDeductions()));
        payroll.setMonth(dto.getMonth());

        Payroll updated =payrollRepository.save(payroll);
        return mapToDto(updated);
    }

    //delete payroll
    public void deletePayroll(Long id){
        if(!payrollRepository.existsById(id)){
            throw  new RuntimeException("Payroll not found on this ID");
        }
        payrollRepository.deleteById(id);
    }

    //calculate net salary
    public double calculateNetSalary(double salary, double bonus, double deduction){
        return salary + bonus -deduction;
    }

    //map entity to dto
    public PayRollDto mapToDto( Payroll payroll){
        PayRollDto dto = new PayRollDto();
        dto.setId(payroll.getId());
        dto.setUserId(payroll.getUser().getId());
        dto.setSalary(payroll.getSalary());
        dto.setBonuses(payroll.getBonuses());
        dto.setDeductions(payroll.getDeduction());
        dto.setNetSalary(payroll.getNetSalary());
        dto.setMonth(payroll.getMonth());

        return dto;
    }
}
