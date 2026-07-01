package com.hrm.system.service;

import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.User;
import com.hrm.system.repository.EmployeeProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Determines when an employee has completed one year of service for annual leave.
 * Service start date priority: joining date → probation start → account creation.
 */
@Service
public class LeaveEligibilityService {

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

    public LocalDate getServiceStartDate(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        Optional<EmployeeProfile> profile = employeeProfileRepository.findByUserId(user.getId());
        if (profile.isPresent() && profile.get().getJoiningDate() != null) {
            return profile.get().getJoiningDate();
        }

        if (user.getProbationStartDate() != null) {
            return user.getProbationStartDate();
        }

        if (user.getCreatedAt() != null) {
            return user.getCreatedAt().toLocalDate();
        }

        return null;
    }

    /** Eligible on the anniversary of the service start date (inclusive). */
    public boolean hasCompletedOneYear(User user) {
        LocalDate start = getServiceStartDate(user);
        if (start == null) {
            return false;
        }
        return !start.plusYears(1).isAfter(LocalDate.now());
    }
}
