package com.hrm.system.service;

import com.hrm.system.dto.LeaveDto;
import com.hrm.system.model.Leave;
import com.hrm.system.model.LeaveStatus;
import com.hrm.system.model.User;
import com.hrm.system.repository.LeaveRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

public class LeaveService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaveRepository leaveRepository;

    //Apply for leave
    public LeaveDto applyLeave(LeaveDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found on this ID" + dto.getUserId()));

        Leave leave = new Leave();

        leave.setUser(user);
        leave.setType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setStatus(LeaveStatus.valueOf(dto.getStatus()));

        Leave saved = leaveRepository.save(leave);
        return mapToDto(saved);
    }

        //get all leaves
        public List<LeaveDto> getAllLeaves(){
        return leaveRepository.findAll()
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    //Get leave by ID
    public  LeaveDto getLeaveById(Long id){
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found on this ID" + id));
        return mapToDto(leave);

    }

    //get leave by userId
    public List<LeaveDto> getLeaveByUserID(Long id){
        return  leaveRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    //get Leave by status
    public List<LeaveDto> getLeaveByStatus(String status){
        return leaveRepository.findAll()
                .stream().map(this::mapToDto)
                .collect(Collectors.toList());
    }

    //Approve Leave
    public LeaveDto approveLeave(Long id){
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found on this ID" + id));
        if(!leave.getStatus().equals(LeaveStatus.PENDING)){
            throw new RuntimeException("Only PENDING leaves can be approved.");
        }
        leave.setStatus(LeaveStatus.APPROVED);
        return mapToDto(leaveRepository.save(leave));
    }

    //Leave Rejected
    public LeaveDto rejectLeave(Long id){
        Leave leave =leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found on this ID"));
        if(!leave.getStatus().equals(LeaveStatus.PENDING)){
            throw new RuntimeException("Only Pending Leave will rejected");
        }
        leave.setStatus(LeaveStatus.REJECT);
        return mapToDto(leaveRepository.save(leave));
    }

    // update leave if still pending
    public LeaveDto updateLeave(Long id, LeaveDto dto){
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User Not found on this ID" + id));
        leave.setType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setStatus(LeaveStatus.valueOf(dto.getStatus()));

        return mapToDto(leaveRepository.save(leave));
    }

    //update delete if still pending
    public  void deleteLeave(Long id){
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("user is not found on this ID" +id));

        if(!leave.getStatus().equals(LeaveStatus.PENDING)){
            throw new RuntimeException("Cannot delete a leave that is already \" + leave.getStatus()");
        }
        leaveRepository.deleteById(id);
    }

    //map entity to dto
    public  LeaveDto mapToDto(Leave leave){
        LeaveDto dto = new LeaveDto();
        dto.setId(leave.getId());
        dto.setUserId(leave.getUser().getId());
        dto.setLeaveType(leave.getType());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setStatus(String.valueOf(leave.getStatus()));
        return dto;
    }

}
