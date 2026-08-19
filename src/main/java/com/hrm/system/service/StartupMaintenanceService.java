package com.hrm.system.service;

import com.hrm.system.model.User;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Heavy startup work (leave balances, probation sync) runs in the background so
 * Railway / HTTP endpoints like login are not blocked after deploy.
 */
@Service
public class StartupMaintenanceService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private LeavePolicyService leavePolicyService;

    @Autowired
    private ProbationService probationService;

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

    @Async("startupTaskExecutor")
    @Transactional
    public void runDeferredStartupMaintenance() {
        try {
            System.out.println("🔧 Running database schema cleanup for legacy payroll columns...");
            jdbcTemplate.execute("ALTER TABLE payroll DROP COLUMN IF EXISTS month");
            jdbcTemplate.execute("ALTER TABLE payroll DROP COLUMN IF EXISTS year");
            jdbcTemplate.execute("ALTER TABLE payroll DROP COLUMN IF EXISTS salary");
            jdbcTemplate.execute("ALTER TABLE payroll DROP COLUMN IF EXISTS deduction");
            jdbcTemplate.execute("ALTER TABLE payroll DROP COLUMN IF EXISTS bonuses");
            jdbcTemplate.execute("UPDATE leave_balances SET total_days = 0 WHERE (leave_type = 'EIDULFITAR' OR leave_type = 'EIDULAZHA') AND total_days = 3 AND used_days = 0");
            jdbcTemplate.execute("UPDATE leave_policies SET total_days_per_year = 0 WHERE leave_type = 'EIDULFITAR' OR leave_type = 'EIDULAZHA'");
            System.out.println("✅ Legacy database columns cleanup and Eid balance reset finished successfully.");
        } catch (Exception e) {
            System.err.println("⚠️ Legacy database column cleanup failed: " + e.getMessage());
        }

        try {
            int year = LocalDate.now().getYear();
            List<User> allUsers = userRepository.findAll();
            for (User user : allUsers) {
                leaveBalanceService.initializeBalancesForUser(user, year);
                leavePolicyService.ensureEligibilityBalancesForUser(user);
            }
            probationService.syncProbationFromProfiles(employeeProfileRepository.findAllWithUsers());
            System.out.println("✅ Deferred startup maintenance finished for " + allUsers.size() + " users.");
        } catch (Exception e) {
            System.err.println("⚠️ Deferred startup maintenance failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
