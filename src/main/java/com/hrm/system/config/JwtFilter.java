package com.hrm.system.config;

import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.security.CustomUserDetails;
import com.hrm.system.service.UserService;
import com.hrm.system.util.IpUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;

/**
 * Authenticates each request from its Bearer token. Role and enabled status are read from the
 * database, so a demotion or disabled account applies at once rather than when the token expires.
 */
@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${office.allowed.ips:58.65.129.12}")
    private String allowedOfficeIps;

    @Value("${office.allow.localhost:false}")
    private boolean allowLocalhost;

    public JwtFilter(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseClaims(authHeader.substring(7));
            Long userId = JwtUtil.userIdOf(claims);
            CustomUserDetails details = userId != null
                    ? userService.loadUserDetailsById(userId)
                    : (CustomUserDetails) userService.loadUserByUsername(claims.getSubject());
            User user = details.getUser();

            if (!user.isEnabled() || issuedBeforePasswordChange(claims, user)) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired. Please log in again.");
                return;
            }

            boolean privileged = user.getRole() == Role.ADMIN || user.getRole() == Role.SUPERADMIN;
            if (!privileged && !user.isOutsideAccessAllowed()
                    && !IpUtil.isAllowedIp(IpUtil.getClientIp(request), allowedOfficeIps, allowLocalhost)) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "HRM access is restricted to Office Wi-Fi. Please connect to Office Wi-Fi or request remote access from HR.");
                return;
            }

            request.setAttribute("userId", user.getId());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        } catch (ExpiredJwtException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expired. Please log in again.");
            return;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token.");
            return;
        } catch (RuntimeException e) {
            log.error("Authentication failed unexpectedly", e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** JWT timestamps have one-second precision, so both sides are compared in whole seconds. */
    private boolean issuedBeforePasswordChange(Claims claims, User user) {
        if (user.getPasswordChangedAt() == null) return false;
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) return true;
        long changedAtSeconds = user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return issuedAt.getTime() / 1000 < changedAtSeconds;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
