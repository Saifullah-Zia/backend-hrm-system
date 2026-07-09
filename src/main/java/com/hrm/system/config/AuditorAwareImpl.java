package com.hrm.system.config;

import com.hrm.system.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current authenticated user's ID to Spring Data JPA auditing
 * (populates fields annotated with @CreatedBy / @LastModifiedBy).
 *
 * Returns Optional<Long> to match the type of entity @CreatedBy fields.
 * Reads the user ID directly from the CustomUserDetails principal to avoid
 * database queries during @PreUpdate callbacks, which would trigger
 * Hibernate auto-flush and cause StackOverflowError.
 */
@Slf4j
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            log.debug("No authenticated user found; createdBy will be left null");
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return Optional.ofNullable(userDetails.getId());
        }

        log.warn("Principal is not an instance of CustomUserDetails; createdBy will be left null");
        return Optional.empty();
    }
}