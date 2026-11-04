package com.hrm.system.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {

    /**
     * Railway's public edge replaces any client-sent X-Real-IP, so that value is trustworthy. Other
     * headers such as CF-Connecting-IP can be set by anyone and must never be used for access checks.
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        String ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // Proxies append the address they saw, so only the last entry is not client-controlled.
        ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            return ip.substring(ip.lastIndexOf(',') + 1).trim();
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "";
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip.trim());
    }

    public static boolean isAllowedIp(String clientIp, String allowedOfficeIps, boolean allowLocalhost) {
        if (allowedOfficeIps == null || allowedOfficeIps.trim().equals("*")) {
            return true;
        }

        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }

        String trimmedClient = clientIp.trim();

        // Localhost check if allowed
        if (allowLocalhost) {
            if ("127.0.0.1".equals(trimmedClient) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(trimmedClient) || "::1".equals(trimmedClient)) {
                return true;
            }
        }

        String[] allowedList = allowedOfficeIps.split(",");
        for (String allowed : allowedList) {
            String target = allowed.trim();
            if (target.equalsIgnoreCase(trimmedClient)) {
                return true;
            }
        }

        return false;
    }
}
