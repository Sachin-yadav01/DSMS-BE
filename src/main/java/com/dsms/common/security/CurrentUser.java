package com.dsms.common.security;

import com.dsms.common.domain.UserRole;
import com.dsms.users.UserAccount;
import com.dsms.users.UserAccountRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserAccountRepository users;

    public CurrentUser(UserAccountRepository users) {
        this.users = users;
    }

    public Optional<DsmsUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DsmsUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public DsmsUserPrincipal requirePrincipal() {
        return principal().orElseThrow(() -> com.dsms.common.exception.Exceptions.unauthorized("Authentication required"));
    }

    public Long requireUserId() {
        return requirePrincipal().getId();
    }

    public UserAccount requireAccount() {
        return users.findById(requireUserId())
                .orElseThrow(() -> com.dsms.common.exception.Exceptions.unauthorized("Authentication required"));
    }

    public boolean hasRole(UserRole role) {
        return principal().map(p -> p.getRole() == role).orElse(false);
    }

    public boolean isAdmin() { return hasRole(UserRole.ADMIN); }
    public boolean isCoordinator() { return hasRole(UserRole.COORDINATOR); }
    public boolean isCandidate() { return hasRole(UserRole.CANDIDATE); }
    public boolean isExpert() { return hasRole(UserRole.EXPERT); }
}
