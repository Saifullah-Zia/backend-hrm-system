package com.hrm.system.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        // Check Cloudflare header first
        String ip = request.getHeader("CF-Connecting-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // Check X-Real-IP header
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // Check X-Forwarded-For header (comma-separated list, first IP is client)
        ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            String[] parts = ip.split(",");
            return parts[0].trim();
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
