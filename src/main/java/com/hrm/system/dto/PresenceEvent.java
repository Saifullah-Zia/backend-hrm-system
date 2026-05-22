package com.hrm.system.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEvent {
    private String employeeId;
    private String status;   // "online", "away", "offline"
}