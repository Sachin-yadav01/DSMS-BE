package com.dsms.common.security;

import com.dsms.common.domain.UserRole;
import com.dsms.users.UserAccount;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class DsmsUserPrincipal implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean active;
    private final boolean mustChangePassword;
    private final String displayName;
    private final String timezone;

    public DsmsUserPrincipal(UserAccount user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.getStatus() == com.dsms.common.domain.UserStatus.ACTIVE;
        this.mustChangePassword = user.isMustChangePassword();
        this.displayName = user.getDisplayName();
        this.timezone = user.getTimezone();
    }

    public Long getId() { return id; }
    public UserRole getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public String getDisplayName() { return displayName; }
    public String getTimezone() { return timezone; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
