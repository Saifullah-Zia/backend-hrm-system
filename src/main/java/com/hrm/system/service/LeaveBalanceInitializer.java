package com.hrm.system;

import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import com.hrm.system.service.LeaveBalanceService;
import com.hrm.system.service.LeavePolicyService;
import com.hrm.system.service.ProbationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class LeaveBalanceInitializer implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private LeavePolicyService leavePolicyService;

    @Autowired
    private ProbationService probationService;

    @Autowired
    private com.hrm.system.repository.EmployeeProfileRepository employeeProfileRepository;

    @Override
    public void run(ApplicationArguments args) {
        int year = LocalDate.now().getYear();
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            leaveBalanceService.initializeBalancesForUser(user, year);
            leavePolicyService.ensureEligibilityBalancesForUser(user);
        }
        probationService.syncProbationFromProfiles(employeeProfileRepository.findAllWithUsers());
        System.out.println("✅ Leave balances initialized for " + allUsers.size() + " users for year " + year);
    }
}