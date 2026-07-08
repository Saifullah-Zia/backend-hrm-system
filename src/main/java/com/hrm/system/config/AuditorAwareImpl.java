package com.hrm.system.config;

import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
 * IMPORTANT: This must return Optional<Long> to match the type of
 * Notice.createdBy (Long) and any other entity's @CreatedBy field of type Long.
 * Previously this returned Optional<String> (the username), which caused:
 *   "Cannot cast java.lang.String to java.lang.Long"
 * because Spring Data tries to assign the auditor value directly onto the
 * Long-typed field via reflection.
 *
 * authentication.getName() gives us the username/email, not the numeric id,
 * so we look the user up via UserRepository to resolve their Long id.
 */
@Slf4j
@Component("auditorAware")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<Long> {

    private final UserRepository userRepository;

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            // No numeric "system" user id to fall back on — leave createdBy null
            // for unauthenticated/system actions rather than risk another cast error.
            // If you have a dedicated system/service account row in your users table,
            // replace this with Optional.of(<that user's id>).
            log.debug("No authenticated user found; createdBy will be left null");
            return Optional.empty();
        }

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByName(usernameOrEmail))
                .map(User::getId)
                .or(() -> {
                    log.warn("Authenticated principal '{}' not found in users table; createdBy will be left null",
                            usernameOrEmail);
                    return Optional.empty();
                });
    }
}