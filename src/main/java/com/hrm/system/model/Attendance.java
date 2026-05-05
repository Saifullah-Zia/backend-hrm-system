package com.hrm.system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private String status; // e.g. PRESENT, ABSENT
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}