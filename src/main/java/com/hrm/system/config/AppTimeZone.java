package com.hrm.system.config;

import java.time.ZoneId;

public class AppTimeZone {
    // Pakistan Standard Time — UTC+5, no DST
    public static final ZoneId PKT = ZoneId.of("Asia/Karachi");

    private AppTimeZone() {} // utility class
}