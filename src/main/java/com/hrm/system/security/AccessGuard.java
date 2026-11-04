package com.hrm.system.security;

import com.hrm.system.repository.EmployeeProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Ownership checks: an employee may only touch their own records, while HR may touch any. */
@Component
public class AccessGuard {

    private final EmployeeProfileRepository employeeProfileRepository;

    public AccessGuard(EmployeeProfileRepository employeeProfileRepository) {
        this.employeeProfileRepository = employeeProfileRepository;
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return details.getId();
        }
        throw new AccessDeniedException("Not authenticated");
    }

    public boolean isPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    /** Allows HR, or the user whose id is given. */
    public void requireSelfOrPrivileged(Long userId) {
        if (isPrivileged()) return;
        if (userId == null || !userId.equals(currentUserId())) {
            throw new AccessDeniedException("You can only access your own records");
        }
    }

    /** Allows HR, or the user who owns the employee profile with the given id. */
    public void requireOwnProfileOrPrivileged(Long profileId) {
        if (isPrivileged()) return;
        if (profileId == null || !ownsProfile(profileId)) {
            throw new AccessDeniedException("You can only access your own records");
        }
    }

    public boolean ownsProfile(Long profileId) {
        return employeeProfileRepository.findByUserId(currentUserId())
                .map(profile -> profileId.equals(profile.getId()))
                .orElse(false);
    }
}
