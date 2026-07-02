package com.hrm.system;

import com.hrm.system.service.StartupMaintenanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class LeaveBalanceInitializer implements ApplicationRunner {

    @Autowired
    private StartupMaintenanceService startupMaintenanceService;

    @Override
    public void run(ApplicationArguments args) {
        // Do not block HTTP readiness — run in background (see StartupMaintenanceService).
        startupMaintenanceService.runDeferredStartupMaintenance();
        System.out.println("✅ Startup maintenance scheduled in background.");
    }
}