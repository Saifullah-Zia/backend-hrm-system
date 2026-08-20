package com.hrm.system.config;

import com.hrm.system.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @org.springframework.beans.factory.annotation.Value("${office.allowed.ips:58.65.129.12}")
    private String allowedOfficeIps;

    @org.springframework.beans.factory.annotation.Value("${office.allow.localhost:true}")
    private boolean allowLocalhost;

    public JwtFilter(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    private String getClientIp(HttpServletRequest request) {
        return com.hrm.system.util.IpUtil.getClientIp(request);
    }

    private boolean isOfficeIp(String clientIp) {
        return com.hrm.system.util.IpUtil.isAllowedIp(clientIp, allowedOfficeIps, allowLocalhost);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        System.out.println("[JwtFilter] Request Path: " + path);

        if (path.startsWith("/api/auth") || path.startsWith("/api/settings/my-ip")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String authHeader = request.getHeader("Authorization");
            System.out.println("[JwtFilter] Authorization Header: " + (authHeader != null ? "Present (Length: " + authHeader.length() + ")" : "NULL"));

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String username = jwtUtil.extractUsername(token);
                System.out.println("[JwtFilter] Username extracted: " + username);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    boolean isValid = jwtUtil.validateToken(token);
                    System.out.println("[JwtFilter] Is token valid: " + isValid);

                    if (isValid) {
                        UserDetails userDetails = userService.loadUserByUsername(username);

                        String role = jwtUtil.extractRole(token);
                        System.out.println("[JwtFilter] Role extracted: " + role);

                        // ✅ Location / Office Wi-Fi access restriction for employees
                        if (userDetails instanceof com.hrm.system.security.CustomUserDetails) {
                            com.hrm.system.security.CustomUserDetails customUser = (com.hrm.system.security.CustomUserDetails) userDetails;
                            com.hrm.system.model.User userEntity = customUser.getUser();
                            
                            boolean isPrivileged = "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
                            boolean isRemoteAllowed = userEntity.isOutsideAccessAllowed();
                            String clientIp = getClientIp(request);
                            boolean isOffice = isOfficeIp(clientIp);

                            if (!isPrivileged && !isRemoteAllowed && !isOffice) {
                                System.out.println("[JwtFilter] Blocked employee " + username + " from IP " + clientIp + " (Outside Office Wi-Fi)");
                                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "HRM access is restricted to Office Wi-Fi. Please connect to Office Wi-Fi or request remote access from HR.");
                                return;
                            }
                        }

                        // ✅ Extract userId from JWT and store in request attribute
                        Long userId = jwtUtil.extractUserId(token);
                        if (userId != null) {
                            request.setAttribute("userId", userId);
                        }

                        List<SimpleGrantedAuthority> authorities = List.of(
                                new SimpleGrantedAuthority("ROLE_" + role)
                        );

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        authorities
                                );

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        System.out.println("[JwtFilter] Authentication successfully set in SecurityContextHolder for user: " + username);
                    } else {
                        System.out.println("[JwtFilter] Token validation failed, skipping setting authentication.");
                    }
                }
            } else {
                System.out.println("[JwtFilter] Authorization header is missing or does not start with 'Bearer '");
            }
        } catch (ExpiredJwtException e) {
            System.out.println("[JwtFilter] ExpiredJwtException: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expired. Please log in again.");
            return;
        } catch (JwtException e) {
            System.out.println("[JwtFilter] JwtException: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token.");
            return;
        } catch (Exception e) {
            System.err.println("JWT Filter Error: " + e.getMessage());
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error: " + e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}